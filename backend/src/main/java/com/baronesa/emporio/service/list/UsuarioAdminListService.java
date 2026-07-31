package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioAdminRepository;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.util.FilterSpecificationBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioAdminListService extends BaseListService<Usuario> {

    private final UsuarioAdminRepository repo;
    private final DynamicFormRegistry formConfigRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected JpaSpecificationExecutor<Usuario> getRepository() {
        return repo;
    }

    @Override
    protected Class<Usuario> getEntityClass() {
        return Usuario.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver r) {
        return formConfigRegistry.getConfig("usuarios-admin");
    }

    @Override
    protected Map<String, String> getFieldMappings() {
        return Map.of(
                "grupo", "grupoUsuario.descricao"
        );
    }

    // Método para listar todos (ADMIN + FUNCIONARIO)
    @Override
    public Map<String, Object> list(int pagina,
                                    int tamanho,
                                    String ordenacao,
                                    String direcao,
                                    String filtroJson,
                                    MessageResolver resolver) {
        return listWithFilter(pagina, tamanho, ordenacao, direcao, filtroJson, resolver, "all");
    }

    // Método adicional para listar apenas ADMIN
    public Map<String, Object> listAdminsOnly(int pagina,
                                              int tamanho,
                                              String ordenacao,
                                              String direcao,
                                              String filtroJson,
                                              MessageResolver resolver) {
        return listWithFilter(pagina, tamanho, ordenacao, direcao, filtroJson, resolver, "admin");
    }

    private Map<String, Object> listWithFilter(int pagina,
                                               int tamanho,
                                               String ordenacao,
                                               String direcao,
                                               String filtroJson,
                                               MessageResolver resolver,
                                               String filterType) {

        String sortField = ordenacao;
        if (ordenacao != null && !ordenacao.isBlank()) {
            sortField = getFieldMappings().getOrDefault(ordenacao, ordenacao);
        }

        Sort sort = (sortField != null && !sortField.isBlank())
                ? Sort.by(Sort.Direction.fromString(direcao), sortField)
                : Sort.unsorted();

        Pageable pageable = PageRequest.of(pagina, tamanho, sort);

        Specification<Usuario> scopeSpec = buildRoleScopeSpec(filterType);
        Specification<Usuario> filterSpec = new FilterSpecificationBuilder<>(getEntityClass(), getFieldMappings())
                .build(filtroJson);
        Specification<Usuario> rolesSpec = buildRolesFilterSpec(filtroJson);

        Specification<Usuario> finalSpec = Specification.where(scopeSpec);
        if (filterSpec != null) {
            finalSpec = finalSpec.and(filterSpec);
        }
        if (rolesSpec != null) {
            finalSpec = finalSpec.and(rolesSpec);
        }

        Page<Usuario> page = repo.findAll(finalSpec, pageable);

        List<Map<String, Object>> tableData = page.getContent().stream()
                .map(this::entityToRow)
                .toList();

        Map<String, Object> response =
                getFormConfig(resolver).createResponse(tableData);

        response.put("totalElementos", page.getTotalElements());
        response.put("totalPaginas", page.getTotalPages());
        return response;
    }

    public List<Map<String, Object>> listarParaTabela() {
        // Buscar todos ADMIN + FUNCIONARIO sem paginação
        return repo.findAllAdminAndFuncionario(Pageable.unpaged())
                .getContent()
                .stream()
                .map(this::entityToMapSafe)
                .collect(Collectors.toList());
    }

    // Método auxiliar para tratar nulos
    private Map<String, Object> entityToMapSafe(Usuario u) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", u.getId() != null ? u.getId() : 0L);
        row.put("nome", u.getNome() != null ? u.getNome() : "");
        row.put("email", u.getEmail() != null ? u.getEmail() : "");
        row.put("telefone", u.getTelefone() != null ? u.getTelefone() : "");
        row.put("roles", u.getRoles() != null && !u.getRoles().isEmpty()
                ? u.getRoles().stream().map(Enum::name).collect(Collectors.joining(", "))
                : "");
        row.put("grupo", (u.getGrupoUsuario() != null && u.getGrupoUsuario().getDescricao() != null)
                ? u.getGrupoUsuario().getDescricao()
                : "");
        row.put("ativo", u.getAtivo() != null ? u.getAtivo() : false);
        row.put("emailVerificado", u.getEmailVerificado() != null ? u.getEmailVerificado() : false);
        row.put("ultimoLogin", u.getUltimoLogin() != null ? u.getUltimoLogin() : "");
        return row;
    }

    @Override
    protected Map<String, Object> entityToRow(Usuario u) {
        Map<String, Object> row = new LinkedHashMap<>();

        row.put("id", u.getId());
        row.put("nome", u.getNome());
        row.put("email", u.getEmail());
        row.put("telefone", u.getTelefone());
        row.put("roles", u.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", ")));
        row.put("grupo", u.getGrupoUsuario() != null ? u.getGrupoUsuario().getDescricao() : null);
        row.put("ativo", u.getAtivo());
        row.put("emailVerificado", u.getEmailVerificado());
        row.put("ultimoLogin", u.getUltimoLogin());

        return row;
    }

    private Specification<Usuario> buildRoleScopeSpec(String filterType) {
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Expression<java.util.Collection<Usuario.Role>> rolesPath = root.get("roles");
            if ("admin".equals(filterType)) {
                return cb.isMember(Usuario.Role.ADMIN, rolesPath);
            }
            return cb.or(
                    cb.isMember(Usuario.Role.ADMIN, rolesPath),
                    cb.isMember(Usuario.Role.FUNCIONARIO, rolesPath),
                    cb.isMember(Usuario.Role.KDS, rolesPath),
                    cb.isMember(Usuario.Role.WAITER, rolesPath),
                    cb.isMember(Usuario.Role.CAIXA, rolesPath)
            );
        };
    }

    private Specification<Usuario> buildRolesFilterSpec(String filtroJson) {
        if (filtroJson == null || filtroJson.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> filters = mapper.readValue(filtroJson, new TypeReference<>() {});
            Object rawFilter = filters.get("roles");
            if (!(rawFilter instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> filter = (Map<String, Object>) rawFilter;

            Object activeFlag = filter.get("active");
            if (activeFlag != null && !Boolean.parseBoolean(String.valueOf(activeFlag))) {
                return null;
            }

            String operator = String.valueOf(filter.get("operator"));
            Object valueObj = filter.get("value");
            List<Usuario.Role> roles = parseRoles(valueObj);
            if (roles.isEmpty()) {
                return null;
            }

            boolean isNegated = operator != null
                    && (operator.equalsIgnoreCase("notEquals") || operator.equalsIgnoreCase("notContains"));

            return (root, query, cb) -> {
                query.distinct(true);
                jakarta.persistence.criteria.Expression<java.util.Collection<Usuario.Role>> rolesPath = root.get("roles");
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                for (Usuario.Role role : roles) {
                    var memberPredicate = cb.isMember(role, rolesPath);
                    predicates.add(isNegated ? cb.not(memberPredicate) : memberPredicate);
                }
                return isNegated
                        ? cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]))
                        : cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };
        } catch (Exception e) {
            log.warn("Erro ao interpretar filtro de roles para usuarios-admin: {}", e.getMessage());
            return null;
        }
    }

    private List<Usuario.Role> parseRoles(Object valueObj) {
        List<String> values = new ArrayList<>();
        if (valueObj instanceof Collection<?> collection) {
            for (Object v : collection) {
                if (v != null && !v.toString().isBlank()) {
                    values.add(v.toString());
                }
            }
        } else if (valueObj != null) {
            String raw = valueObj.toString().trim();
            if (!raw.isBlank()) {
                for (String part : raw.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isBlank()) {
                        values.add(trimmed);
                    }
                }
            }
        }

        List<Usuario.Role> roles = new ArrayList<>();
        for (String value : values) {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            try {
                roles.add(Usuario.Role.valueOf(normalized));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return roles;
    }
}
