package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.MovimentoEstoque;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.enums.TipoMovimentoEstoque;
import com.baronesa.emporio.repository.MovimentoEstoqueRepository;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.util.FilterSpecificationBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovimentoEstoqueListService extends BaseListService<MovimentoEstoque> {
    private final MovimentoEstoqueRepository repo;
    private final DynamicFormRegistry formConfigRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Set<String> ALLOWED_FILTER_FIELDS = new LinkedHashSet<>(List.of(
            "$quick",
            "dataMovimento",
            "tipoMovimento",
            "produto",
            "sku",
            "usuario",
            "documentoReferencia",
            "documento",
            "quantidade",
            "estoqueAtual"
    ));

    private static final List<String> FILTER_FIELDS = List.of(
            "dataMovimento",
            "tipoMovimento",
            "produto",
            "sku",
            "usuario",
            "documentoReferencia",
            "quantidade",
            "estoqueAtual"
    );

    @Override
    protected JpaSpecificationExecutor<MovimentoEstoque> getRepository() {
        return repo;
    }

    @Override
    protected Class<MovimentoEstoque> getEntityClass() {
        return MovimentoEstoque.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver r) {
        return formConfigRegistry.getConfig("movimento-estoque");
    }

    @Override
    protected Map<String, String> getFieldMappings() {
        return Map.of(
                "produto", "sku.produto.nome",
                "sku", "sku.sku",
                "usuario", "usuario.nome",
                "documento", "documentoReferencia"
        );
    }

    @Override
    protected Map<String, Object> entityToRow(MovimentoEstoque movimento) {
        Map<String, Object> row = new LinkedHashMap<>();

        row.put("id", movimento.getId() != null ? movimento.getId() : 0L);
        row.put("dataMovimento", movimento.getDataMovimento() != null ? movimento.getDataMovimento() : "");
        row.put("produto", movimento.getSku() != null && movimento.getSku().getProduto() != null && movimento.getSku().getProduto().getNome() != null
                ? movimento.getSku().getProduto().getNome() : "");
        row.put("sku", movimento.getSku() != null ? formatarSku(movimento.getSku()) : "");
        row.put("tipoMovimento", TipoMovimentoEstoque.fromCodigo(movimento.getTipoMovimento()) != null
                ? TipoMovimentoEstoque.fromCodigo(movimento.getTipoMovimento()).getDescricao() : "");
        // Quantidade/estoques: para INSUMO, usar campos em unidade base persistidos no movimento
        if (movimento.getSku() != null && movimento.getSku().getProduto() != null
                && Boolean.TRUE.equals(movimento.getSku().getProduto().getInsumo())) {
            String unidade;
            var ub = movimento.getSku().getProduto().getUnidadeBase();
            if (ub != null) {
                switch (ub) {
                    case MILILITRO -> unidade = "ml";
                    case GRAMA -> unidade = "g";
                    default -> unidade = "un";
                }
            } else {
                unidade = "un";
            }
            // quantidadeBase pode ser negativa ou positiva dependendo do sentido
            Integer fator = 1;
            if (movimento.getSku().getEmbalagem() != null && movimento.getSku().getEmbalagem().getFatorBase() != null
                    && movimento.getSku().getEmbalagem().getFatorBase() > 0) {
                fator = movimento.getSku().getEmbalagem().getFatorBase();
            } else if (movimento.getSku().getProduto().getEmbalagens() != null) {
                for (var e : movimento.getSku().getProduto().getEmbalagens()) {
                    if (Boolean.TRUE.equals(e.getPrincipal()) && e.getFatorBase() != null && e.getFatorBase() > 0) {
                        fator = e.getFatorBase();
                        break;
                    }
                }
            }

            Integer qBase = movimento.getQuantidadeBase();
            if (qBase == null && movimento.getQuantidade() != null) {
                qBase = movimento.getQuantidade().intValue() * fator;
            }
            String qStr = (qBase != null && qBase < 0 ? "-" : "") + Math.abs(qBase != null ? qBase : 0) + unidade;
            row.put("quantidade", qStr);

            Integer eaBase = movimento.getEstoqueAnteriorBase();
            if (eaBase == null && movimento.getEstoqueAnterior() != null) {
                eaBase = movimento.getEstoqueAnterior().intValue() * fator;
            }
            row.put("estoqueAnterior", (eaBase != null ? eaBase : 0) + unidade);

            Integer eBase = movimento.getEstoqueAtualBase();
            if (eBase == null && movimento.getEstoqueAtual() != null) {
                eBase = movimento.getEstoqueAtual().intValue() * fator;
            }
            row.put("estoqueAtual", (eBase != null ? eBase : 0) + unidade);
        } else {
            row.put("quantidade", movimento.getQuantidade() != null ? movimento.getQuantidade() : 0);
            row.put("estoqueAnterior", movimento.getEstoqueAnterior() != null ? movimento.getEstoqueAnterior() : 0);
            row.put("estoqueAtual", movimento.getEstoqueAtual() != null ? movimento.getEstoqueAtual() : 0);
        }
        row.put("documento", movimento.getDocumentoReferencia() != null ? movimento.getDocumentoReferencia() : "");
        row.put("observacao", movimento.getObservacao() != null ? movimento.getObservacao() : "");
        row.put("usuario", movimento.getUsuario() != null && movimento.getUsuario().getNome() != null
                ? movimento.getUsuario().getNome() : "");

        return row;
    }

    private String formatarSku(ProdutoSKU sku) {
        StringBuilder sb = new StringBuilder();
        sb.append(sku.getSku() != null ? sku.getSku() : "");

        if (sku.getVariacao() != null && !sku.getVariacao().isBlank()) {
            sb.append(" - ").append(sku.getVariacao());
        }

        return sb.toString();
    }

    // MÉTODO ANTIGO - REMOVIDO
    @Deprecated
    public List<Map<String, Object>> listarParaTabela() {
        // Este método não deve ser usado pois retorna TODOS os registros
        throw new UnsupportedOperationException(
                "Use o método listarPaginado() com paginação em vez de listarParaTabela()"
        );
    }

    // NOVO MÉTODO COM PAGINAÇÃO
    public Map<String, Object> listarPaginado(int pagina, int tamanho, String ordenacao, String direcao, String filtroJson) {
        String sortField = ordenacao;
        if (ordenacao != null && !ordenacao.isBlank()) {
            sortField = getFieldMappings().getOrDefault(ordenacao, ordenacao);
        }

        Sort sort = (sortField != null && !sortField.isBlank())
                ? Sort.by(Sort.Direction.fromString(direcao), sortField)
                : Sort.unsorted();

        Pageable pageable = PageRequest.of(pagina, tamanho, sort);

        // Usar Specification se houver filtro
        Page<MovimentoEstoque> page;
        Specification<MovimentoEstoque> spec = buildSpecification(filtroJson);
        if (spec != null) {
            page = repo.findAll(spec, pageable);
        } else {
            page = repo.findAll(pageable);
        }

        // Converter para lista de mapas
        List<Map<String, Object>> tableData = page.getContent().stream()
                .map(this::entityToRow)
                .collect(Collectors.toList());

        // Criar resposta usando as informações do Page
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("table_data", tableData);
        response.put("totalElementos", page.getTotalElements()); // Total de registros no banco
        response.put("totalPaginas", page.getTotalPages());      // Total de páginas
        response.put("paginaAtual", page.getNumber());           // Página atual (0-based)
        response.put("tamanhoPagina", page.getSize());           // Tamanho da página
        response.put("filter_fields", FILTER_FIELDS);

        // Log para debug
        System.out.println("DEBUG Paginação - Total elementos: " + page.getTotalElements());
        System.out.println("DEBUG Paginação - Total páginas: " + page.getTotalPages());
        System.out.println("DEBUG Paginação - Página atual: " + page.getNumber());
        System.out.println("DEBUG Paginação - Tamanho página: " + page.getSize());
        System.out.println("DEBUG Paginação - Registros retornados: " + tableData.size());

        return response;
    }

    @Override
    public Map<String, Object> list(int pagina,
                                    int tamanho,
                                    String ordenacao,
                                    String direcao,
                                    String filtroJson,
                                    MessageResolver resolver) {
        String sortField = ordenacao;
        if (ordenacao != null && !ordenacao.isBlank()) {
            sortField = getFieldMappings().getOrDefault(ordenacao, ordenacao);
        }

        Sort sort = (sortField != null && !sortField.isBlank())
                ? Sort.by(Sort.Direction.fromString(direcao), sortField)
                : Sort.unsorted();

        Pageable pageable = PageRequest.of(pagina, tamanho, sort);

        Specification<MovimentoEstoque> spec = buildSpecification(filtroJson);
        Page<MovimentoEstoque> page = spec != null
                ? repo.findAll(spec, pageable)
                : repo.findAll(pageable);

        List<Map<String, Object>> tableData = page.getContent().stream()
                .map(this::entityToRow)
                .toList();

        Map<String, Object> response = new HashMap<>();

        BaseFormConfig formConfig = getFormConfig(resolver);
        if (formConfig != null) {
            response = formConfig.createResponse(tableData);
        } else {
            response.put("table_data", tableData);
        }

        response.put("totalElementos", page.getTotalElements());
        response.put("totalPaginas", page.getTotalPages());
        response.put("paginaAtual", page.getNumber());
        response.put("tamanhoPagina", page.getSize());
        response.put("filter_fields", FILTER_FIELDS);

        if (ordenacao != null && !ordenacao.isBlank()) {
            String tableOrder = ordenacao + " " + direcao.toUpperCase();
            response.put("table_order", tableOrder);
        }

        return response;
    }

    private Specification<MovimentoEstoque> buildSpecification(String filtroJson) {
        String sanitizedFilter = sanitizeFilterJson(filtroJson);
        Specification<MovimentoEstoque> spec = new FilterSpecificationBuilder<>(MovimentoEstoque.class, getFieldMappings())
                .build(sanitizedFilter);
        Specification<MovimentoEstoque> tipoMovimentoSpec = buildTipoMovimentoSpec(filtroJson);

        if (spec != null && tipoMovimentoSpec != null) {
            return spec.and(tipoMovimentoSpec);
        }
        if (spec != null) {
            return spec;
        }
        return tipoMovimentoSpec;
    }

    private String sanitizeFilterJson(String filtroJson) {
        if (filtroJson == null || filtroJson.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> filters = mapper.readValue(filtroJson, new TypeReference<>() {});
            Map<String, Object> sanitized = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                if (ALLOWED_FILTER_FIELDS.contains(entry.getKey())) {
                    if ("tipoMovimento".equals(entry.getKey()) && shouldHandleTipoMovimentoByName(entry.getValue())) {
                        continue;
                    }
                    sanitized.put(entry.getKey(), entry.getValue());
                }
            }

            if (sanitized.isEmpty()) {
                return null;
            }

            return mapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            return filtroJson;
        }
    }

    private Specification<MovimentoEstoque> buildTipoMovimentoSpec(String filtroJson) {
        if (filtroJson == null || filtroJson.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> filters = mapper.readValue(filtroJson, new TypeReference<>() {});
            Object rawFilter = filters.get("tipoMovimento");
            if (!(rawFilter instanceof Map)) {
                return null;
            }

            if (!shouldHandleTipoMovimentoByName(rawFilter)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> filter = (Map<String, Object>) rawFilter;
            String operator = asString(filter.get("operator"));
            String value = asString(filter.get("value"));
            if (value == null || value.isBlank()) {
                return null;
            }

            String needle = value.toLowerCase();
            List<Integer> codes = new java.util.ArrayList<>();
            for (TipoMovimentoEstoque tipo : TipoMovimentoEstoque.values()) {
                String descricao = tipo.getDescricao() != null ? tipo.getDescricao().toLowerCase() : "";
                boolean matches = switch (operator) {
                    case "equals", "notEquals" -> descricao.equals(needle);
                    case "startsWith" -> descricao.startsWith(needle);
                    case "endsWith" -> descricao.endsWith(needle);
                    default -> descricao.contains(needle);
                };
                if (matches) {
                    codes.add(tipo.getCodigo());
                }
            }

            boolean negate = "notEquals".equalsIgnoreCase(operator) || "notContains".equalsIgnoreCase(operator);

            if (codes.isEmpty()) {
                if (negate) {
                    return null;
                }
                return (root, query, cb) -> cb.disjunction();
            }

            return (root, query, cb) -> {
                var path = root.get("tipoMovimento");
                jakarta.persistence.criteria.Predicate predicate = path.in(codes);
                return negate ? cb.not(predicate) : predicate;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private boolean shouldHandleTipoMovimentoByName(Object rawFilter) {
        if (!(rawFilter instanceof Map)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) rawFilter;

        Object activeFlag = filter.get("active");
        if (activeFlag != null && !Boolean.parseBoolean(String.valueOf(activeFlag))) {
            return false;
        }

        String operator = asString(filter.get("operator"));
        Object valueObj = filter.get("value");
        if (operator == null || valueObj == null) {
            return false;
        }
        String value = asString(valueObj);
        if (value == null || value.isBlank()) {
            return false;
        }

        boolean numeric = isNumeric(value);
        if (!numeric) {
            return true;
        }
        return "contains".equalsIgnoreCase(operator)
                || "notContains".equalsIgnoreCase(operator)
                || "startsWith".equalsIgnoreCase(operator)
                || "endsWith".equalsIgnoreCase(operator);
    }

    private boolean isNumeric(String value) {
        return value != null && value.matches("-?\\d+");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
