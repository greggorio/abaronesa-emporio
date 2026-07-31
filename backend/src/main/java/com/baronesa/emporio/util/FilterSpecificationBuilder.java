package com.baronesa.emporio.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.entity.Estoque;
import com.baronesa.emporio.entity.ProdutoSKU;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

public class FilterSpecificationBuilder<T> {

    private final Class<T> entityClass;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> fieldMappings;

    public FilterSpecificationBuilder(Class<T> entityClass) {
        this(entityClass, Map.of());
    }

    public FilterSpecificationBuilder(Class<T> entityClass, Map<String, String> fieldMappings) {
        this.entityClass = entityClass;
        this.fieldMappings = fieldMappings != null ? fieldMappings : Map.of();
    }

    public Specification<T> build(String filterJson) {
        if (filterJson == null || filterJson.trim().isEmpty()) {
            return null;
        }

        try {
            // Permitir valores heterogêneos para suportar a chave reservada "$quick"
            Map<String, Object> filters = mapper.readValue(filterJson, new TypeReference<>() {});

            return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
                List<Predicate> andPredicates = new ArrayList<>();
                List<Predicate> quickOrPredicates = new ArrayList<>();

                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    String field = entry.getKey();
                    Object rawFilter = entry.getValue();

                    // Suporte a busca rápida global via chave reservada
                    if ("$quick".equals(field)) {
                        String quickValue = null;
                        if (rawFilter instanceof Map) {
                            Object v = ((Map<?, ?>) rawFilter).get("value");
                            if (v != null) quickValue = v.toString();
                        } else if (rawFilter != null) {
                            quickValue = rawFilter.toString();
                        }

                        if (quickValue != null && !quickValue.isBlank()) {
                            String pattern = "%" + quickValue.toLowerCase() + "%";
                            // OR em todos os atributos String da entidade
                            Set<Attribute<? super T, ?>> attrs = root.getModel().getAttributes();
                            Set<String> seenPaths = new java.util.LinkedHashSet<>();
                            for (Attribute<? super T, ?> attr : attrs) {
                                if (attr.getJavaType() == String.class) {
                                    String attrName = attr.getName();
                                    if (!seenPaths.add(attrName)) {
                                        continue;
                                    }
                                    Path<?> p = root.get(attrName);
                                    Expression<String> lowerExp = cb.lower(p.as(String.class));
                                    quickOrPredicates.add(cb.like(lowerExp, pattern));
                                }
                            }
                            if (!fieldMappings.isEmpty()) {
                                for (String mapped : new java.util.LinkedHashSet<>(fieldMappings.values())) {
                                    if (mapped == null || mapped.isBlank() || !seenPaths.add(mapped)) {
                                        continue;
                                    }
                                    Path<?> mappedPath = resolvePath(root, mapped);
                                    if (mappedPath != null && mappedPath.getJavaType() == String.class) {
                                        Expression<String> lowerExp = cb.lower(mappedPath.as(String.class));
                                        quickOrPredicates.add(cb.like(lowerExp, pattern));
                                    }
                                }
                            }
                        }
                        continue;
                    }

                    // Filtros padrão campo -> { operator, value, value2 }
                    if (!(rawFilter instanceof Map)) {
                        continue; // ignora formatos inválidos para filtros de campo
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> filter = (Map<String, Object>) rawFilter;

                    // Respeita flag "active" enviada pelo front
                    Object activeFlag = filter.get("active");
                    if (activeFlag != null && !Boolean.parseBoolean(asString(activeFlag))) {
                        continue;
                    }

                    String operator = asString(filter.get("operator"));
                    Object valueObj = filter.get("value");
                    Object value2Obj = filter.get("value2");

                    // Alguns operadores (ex.: today) não exigem valor
                    if ((valueObj == null || (valueObj instanceof String s && s.isBlank()))
                            && (operator == null || !operator.equals("today"))) {
                        continue;
                    }

                    // Tratamento especial para "estoque" refletindo a mesma lógica de exibição
                    if ("estoque".equals(field)) {
                        Predicate estoquePredicate = buildEstoquePredicate(operator, valueObj, value2Obj, root, query, cb);
                        if (estoquePredicate != null) {
                            andPredicates.add(estoquePredicate);
                        }
                        continue;
                    }

                    Path<?> path = resolvePath(root, field);
                    if (path == null) {
                        continue;
                    }

                    Class<?> javaType = path.getJavaType();
                    switch (operator) {
                        case "equals" -> {
                            // Para LocalDateTime, comparar apenas a data (ignorando hora)
                            if (javaType == LocalDateTime.class) {
                                LocalDate date = parseLocalDate(asString(valueObj));
                                if (date != null) {
                                    LocalDateTime start = date.atStartOfDay();
                                    LocalDateTime end = date.atTime(23, 59, 59, 999_000_000);
                                    andPredicates.add(cb.between(path.as(LocalDateTime.class), start, end));
                                }
                            } else {
                                andPredicates.add(cb.equal(path, convert(valueObj, javaType)));
                            }
                        }
                        case "notEquals" -> andPredicates.add(cb.notEqual(path, convert(valueObj, javaType)));
                        case "contains" -> {
                            if (javaType == String.class) {
                                String v = asString(valueObj);
                                andPredicates.add(cb.like(cb.lower(path.as(String.class)), "%" + v.toLowerCase() + "%"));
                            }
                        }
                        case "notContains" -> {
                            if (javaType == String.class) {
                                String v = asString(valueObj);
                                andPredicates.add(cb.notLike(cb.lower(path.as(String.class)), "%" + v.toLowerCase() + "%"));
                            }
                        }
                        case "startsWith" -> {
                            if (javaType == String.class) {
                                String v = asString(valueObj);
                                andPredicates.add(cb.like(cb.lower(path.as(String.class)), v.toLowerCase() + "%"));
                            }
                        }
                        case "endsWith" -> {
                            if (javaType == String.class) {
                                String v = asString(valueObj);
                                andPredicates.add(cb.like(cb.lower(path.as(String.class)), "%" + v.toLowerCase()));
                            }
                        }
                        case "lessThan" -> addComparablePredicate(andPredicates, cb, path, javaType, valueObj, CompareOp.LT);
                        case "greaterThan" -> addComparablePredicate(andPredicates, cb, path, javaType, valueObj, CompareOp.GT);
                        case "lessThanOrEqual" -> addComparablePredicate(andPredicates, cb, path, javaType, valueObj, CompareOp.LTE);
                        case "greaterThanOrEqual" -> addComparablePredicate(andPredicates, cb, path, javaType, valueObj, CompareOp.GTE);
                        case "between" -> {
                            if (value2Obj != null && !(value2Obj instanceof String s && s.isBlank())) {
                                addBetweenPredicate(andPredicates, cb, path, javaType, valueObj, value2Obj);
                            }
                        }
                        case "today" -> addTodayPredicate(andPredicates, cb, path, javaType);
                        case "before" -> {
                            // Para LocalDateTime, comparar até o final do dia especificado
                            if (javaType == LocalDateTime.class) {
                                LocalDate date = parseLocalDate(asString(valueObj));
                                if (date != null) {
                                    LocalDateTime endOfDay = date.atTime(23, 59, 59, 999_000_000);
                                    andPredicates.add(cb.lessThanOrEqualTo(path.as(LocalDateTime.class), endOfDay));
                                }
                            } else {
                                addComparablePredicate(andPredicates, cb, path, javaType, valueObj, CompareOp.LTE);
                            }
                        }
                        case "after" -> {
                            // Para LocalDateTime, comparar a partir do início do dia especificado (inclusive)
                            if (javaType == LocalDateTime.class) {
                                LocalDate date = parseLocalDate(asString(valueObj));
                                if (date != null) {
                                    LocalDateTime startOfDay = date.atStartOfDay();
                                    andPredicates.add(cb.greaterThanOrEqualTo(path.as(LocalDateTime.class), startOfDay));
                                }
                            } else {
                                addComparablePredicate(andPredicates, cb, path, javaType, valueObj, CompareOp.GTE);
                            }
                        }
                        default -> {}
                    }
                }

                Predicate andAll = andPredicates.isEmpty() ? cb.conjunction() : cb.and(andPredicates.toArray(new Predicate[0]));
                if (!quickOrPredicates.isEmpty()) {
                    Predicate orQuick = cb.or(quickOrPredicates.toArray(new Predicate[0]));
                    return cb.and(andAll, orQuick);
                }
                return andAll;
            };
        } catch (Exception e) {
            throw new RuntimeException("Erro ao interpretar filtros avançados", e);
        }
    }

    /**
     * Resolve um caminho, permitindo mapeamentos e joins para relacionamentos (ex.: "categoria.nome").
     * Usa JOIN LEFT para não perder registros com relacionamentos nulos.
     */
    private Path<?> resolvePath(Root<T> root, String field) {
        String mappedField = fieldMappings.getOrDefault(field, field);
        String[] parts = mappedField.split("\\.");

        // Campo simples
        if (parts.length == 1) {
            try {
                return root.get(mappedField);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // Campo em relacionamento: cria joins encadeados
        From<?, ?> from = root;
        for (int i = 0; i < parts.length - 1; i++) {
            try {
                from = from.join(parts[i], JoinType.LEFT);
            } catch (IllegalArgumentException e) {
                return null; // relacionamento inválido
            }
        }

        try {
            return from.get(parts[parts.length - 1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Cria predicate para o campo "estoque", combinando:
     * - insumo=true  -> estoqueProduto.quantidadeBase
     * - insumo=false -> soma dos estoques dos SKUs (subquery)
     */
    private Predicate buildEstoquePredicate(String operator,
                                            Object valueObj,
                                            Object value2Obj,
                                            Root<T> root,
                                            CriteriaQuery<?> query,
                                            CriteriaBuilder cb) {
        // Converte valor para Integer (estoque é inteiro)
        Integer v1 = (Integer) convert(valueObj, Integer.class);
        Integer v2 = (Integer) convert(value2Obj, Integer.class);
        if (v1 == null && !"today".equals(operator)) {
            return null;
        }

        // Branch insumo (estoque centralizado)
        Predicate insumoTrue = cb.isTrue(root.get("insumo"));
        Predicate insumoFalse = cb.or(cb.isFalse(root.get("insumo")), cb.isNull(root.get("insumo")));

        Predicate pInsumo = null;
        try {
            From<?, ?> estoqueJoin = root.join("estoqueProduto", JoinType.LEFT);
            Expression<Integer> qtdBase = estoqueJoin.get("quantidadeBase");
            pInsumo = buildComparablePredicate(qtdBase, operator, v1, v2, cb);
        } catch (IllegalArgumentException ignored) {
            // sem join válido, ignora branch insumo
        }

        // Branch não-insumo (soma de SKUs via subquery para refletir estoque real dos SKUs)
        Predicate pNaoInsumo = null;
        try {
            Subquery<Integer> estoqueSkuSum = query.subquery(Integer.class);
            Root<ProdutoSKU> skuRoot = estoqueSkuSum.from(ProdutoSKU.class);
            Join<ProdutoSKU, Estoque> estoqueJoin = skuRoot.join("estoque", JoinType.LEFT);

            Expression<Integer> somaEstoque = cb.coalesce(cb.sum(estoqueJoin.get("quantidade")), 0);
            estoqueSkuSum.select(somaEstoque);
            estoqueSkuSum.where(cb.equal(skuRoot.get("produto"), root));

            pNaoInsumo = buildComparablePredicate(estoqueSkuSum, operator, v1, v2, cb);
        } catch (IllegalArgumentException ignored) {
            // campo não existe, ignora
        }

        List<Predicate> orPredicates = new ArrayList<>();
        if (pInsumo != null) {
            orPredicates.add(cb.and(insumoTrue, pInsumo));
        }
        if (pNaoInsumo != null) {
            orPredicates.add(cb.and(insumoFalse, pNaoInsumo));
        }

        if (orPredicates.isEmpty()) return null;
        if (orPredicates.size() == 1) return orPredicates.get(0);
        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    private Predicate buildComparablePredicate(Expression<Integer> expr,
                                               String operator,
                                               Integer v1,
                                               Integer v2,
                                               CriteriaBuilder cb) {
        if (expr == null) return null;

        return switch (operator) {
            case "equals" -> v1 != null ? cb.equal(expr, v1) : null;
            case "notEquals" -> v1 != null ? cb.notEqual(expr, v1) : null;
            case "lessThan" -> v1 != null ? cb.lessThan(expr, v1) : null;
            case "greaterThan" -> v1 != null ? cb.greaterThan(expr, v1) : null;
            case "lessThanOrEqual" -> v1 != null ? cb.lessThanOrEqualTo(expr, v1) : null;
            case "greaterThanOrEqual" -> v1 != null ? cb.greaterThanOrEqualTo(expr, v1) : null;
            case "between" -> (v1 != null && v2 != null) ? cb.between(expr, v1, v2) : null;
            default -> null;
        };
    }

    private enum CompareOp { LT, GT, LTE, GTE }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addComparablePredicate(List<Predicate> list,
                                        CriteriaBuilder cb,
                                        Path<?> path,
                                        Class<?> javaType,
                                        Object valueObj,
                                        CompareOp op) {
        if (!Comparable.class.isAssignableFrom(javaType)) return;
        Object typed = convert(valueObj, javaType);
        if (typed == null) return;
        Expression<Comparable> expr = (Expression<Comparable>) path.as((Class) javaType);
        Comparable val = (Comparable) typed;
        switch (op) {
            case LT -> list.add(cb.lessThan(expr, val));
            case GT -> list.add(cb.greaterThan(expr, val));
            case LTE -> list.add(cb.lessThanOrEqualTo(expr, val));
            case GTE -> list.add(cb.greaterThanOrEqualTo(expr, val));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addBetweenPredicate(List<Predicate> list,
                                     CriteriaBuilder cb,
                                     Path<?> path,
                                     Class<?> javaType,
                                     Object valueObj,
                                     Object value2Obj) {
        if (!Comparable.class.isAssignableFrom(javaType)) return;

        // Para LocalDateTime, usar início do primeiro dia e final do último dia
        if (javaType == LocalDateTime.class) {
            LocalDateTime startDateTime = parseLocalDateTime(asString(valueObj)); // Use parseLocalDateTime
            LocalDateTime endDateTime = parseLocalDateTime(asString(value2Obj));   // Use parseLocalDateTime
            if (startDateTime != null && endDateTime != null) {
                // Ensure the start date is at the beginning of its day and end date is at the end of its day
                LocalDateTime start = startDateTime.toLocalDate().atStartOfDay();
                LocalDateTime end = endDateTime.toLocalDate().atTime(23, 59, 59, 999_000_000);
                list.add(cb.between(path.as(LocalDateTime.class), start, end));
            }
            return;
        }

        Object typed1 = convert(valueObj, javaType);
        Object typed2 = convert(value2Obj, javaType);
        if (typed1 == null || typed2 == null) return;
        Expression<Comparable> expr = (Expression<Comparable>) path.as((Class) javaType);
        list.add(cb.between(expr, (Comparable) typed1, (Comparable) typed2));
    }

    private void addTodayPredicate(List<Predicate> list,
                                   CriteriaBuilder cb,
                                   Path<?> path,
                                   Class<?> javaType) {
        if (javaType == LocalDate.class) {
            LocalDate today = LocalDate.now();
            list.add(cb.equal(path.as(LocalDate.class), today));
        } else if (javaType == LocalDateTime.class) {
            LocalDate today = LocalDate.now();
            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.atTime(23, 59, 59, 999_000_000);
            list.add(cb.between(path.as(LocalDateTime.class), start, end));
        } else if (javaType == Date.class) {
            LocalDate today = LocalDate.now();
            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.atTime(23, 59, 59, 999_000_000);
            Date d1 = java.sql.Timestamp.valueOf(start);
            Date d2 = java.sql.Timestamp.valueOf(end);
            list.add(cb.between(path.as(Date.class), d1, d2));
        }
    }

    private String asString(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private Object convert(Object raw, Class<?> target) {
        if (raw == null) return null;
        if (target.isInstance(raw)) return raw;

        String s = String.valueOf(raw);
        try {
            if (target == String.class) return s;
            if (target == Boolean.class || target == boolean.class) {
                if (raw instanceof Boolean b) return b;
                return s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("sim");
            }
            if (target == Integer.class || target == int.class) return (raw instanceof Number n) ? n.intValue() : Integer.parseInt(s);
            if (target == Long.class || target == long.class) return (raw instanceof Number n) ? n.longValue() : Long.parseLong(s);
            if (target == Double.class || target == double.class) return (raw instanceof Number n) ? n.doubleValue() : Double.parseDouble(s);
            if (target == Float.class || target == float.class) return (raw instanceof Number n) ? n.floatValue() : Float.parseFloat(s);
            if (target == BigDecimal.class) return (raw instanceof Number n) ? BigDecimal.valueOf(n.doubleValue()) : new BigDecimal(s.replace(',', '.'));
            if (target == BigInteger.class) return (raw instanceof Number n) ? BigInteger.valueOf(n.longValue()) : new BigInteger(s);

            if (target == LocalDate.class) {
                LocalDate d = parseLocalDate(s);
                return d;
            }
            if (target == LocalDateTime.class) {
                LocalDateTime dt = parseLocalDateTime(s);
                return dt;
            }
            if (target == Date.class) {
                // Tenta LocalDateTime e converte
                LocalDateTime dt = parseLocalDateTime(s);
                if (dt == null) {
                    LocalDate d = parseLocalDate(s);
                    if (d != null) dt = d.atStartOfDay();
                }
                return dt != null ? java.sql.Timestamp.valueOf(dt) : null;
            }

            if (target.isEnum()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object enumVal = Enum.valueOf((Class<Enum>) target.asSubclass(Enum.class), s);
                return enumVal;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            // ISO yyyy-MM-dd
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            // dd/MM/yyyy
            DateTimeFormatter br = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return LocalDate.parse(s, br);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private LocalDateTime parseLocalDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            // ISO yyyy-MM-ddTHH:mm:ss
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        try {
            // dd/MM/yyyy HH:mm:ss
            DateTimeFormatter br = DateTimeFormatter.ofPattern("dd/MM/yyyy[ HH:mm[:ss]]");
            return LocalDateTime.parse(s, br);
        } catch (DateTimeParseException ignored) {
        }
        LocalDate fallbackDate = parseLocalDate(s);
        if (fallbackDate != null) {
            return fallbackDate.atStartOfDay();
        }
        return null;
    }
}
