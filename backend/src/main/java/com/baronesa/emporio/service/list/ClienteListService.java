package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.PerfilCliente;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.ClienteRepository;
import com.baronesa.emporio.repository.ClienteSpecificationRepository;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.util.FilterSpecificationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClienteListService extends BaseListService<Usuario> {

    private final ClienteRepository repo;
    private final ClienteSpecificationRepository clienteSpecRepo;
    private final DynamicFormRegistry formConfigRegistry;

    /*–-–-–-–-– Base hooks –-–-–-–-–*/
    @Override
    protected JpaSpecificationExecutor<Usuario> getRepository() {
        return repo;
    }

    @Override
    protected Class<Usuario> getEntityClass() {
        return Usuario.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver resolver) {
        return formConfigRegistry.getConfig("clientes");
    }

    /**
     * Sobrescrever o método list para filtrar apenas usuários com role CLIENTE
     */
    @Override
    public Map<String, Object> list(int pagina,
                                    int tamanho,
                                    String ordenacao,
                                    String direcao,
                                    String filtroJson,
                                    MessageResolver resolver) {

        Sort sort = (ordenacao != null && !ordenacao.isBlank())
                ? Sort.by(Sort.Direction.fromString(direcao), ordenacao)
                : Sort.unsorted();

        Pageable pageable = PageRequest.of(pagina, tamanho, sort);

        // Criar especificação base para filtrar apenas CLIENTES
        Specification<Usuario> clienteSpec = (root, query, cb) -> 
                cb.isMember(Usuario.Role.CLIENTE, root.get("roles"));

        // Aplicar filtros adicionais se fornecidos
        Specification<Usuario> filterSpec = new FilterSpecificationBuilder<>(getEntityClass())
                .build(filtroJson);

        // Combinar especificações
        Specification<Usuario> finalSpec = clienteSpec;
        if (filterSpec != null) {
            finalSpec = Specification.where(clienteSpec).and(filterSpec);
        }

        Page<Usuario> page = getRepository().findAll(finalSpec, pageable);

        List<Map<String, Object>> tableData = page.getContent().stream()
                .map(this::entityToRow)
                .toList();

        // Criar resposta base
        Map<String, Object> response = new java.util.HashMap<>();

        // Se houver FormConfig, usar para criar a resposta
        BaseFormConfig formConfig = getFormConfig(resolver);
        if (formConfig != null) {
            response = formConfig.createResponse(tableData);
        } else {
            // Resposta mínima se não houver FormConfig
            response.put("table_data", tableData);
        }

        // Adicionar informações de paginação
        response.put("totalElementos", page.getTotalElements());
        response.put("totalPaginas", page.getTotalPages());
        response.put("paginaAtual", page.getNumber());
        response.put("tamanhoPagina", page.getSize());

        return response;
    }

    @Override
    protected Map<String, Object> entityToRow(Usuario u) {
        Map<String, Object> row = new LinkedHashMap<>();
        PerfilCliente perfil = u.getPerfilCliente();

        row.put("id",          Objects.requireNonNullElse(u.getId(), 0L));
        row.put("nome",        Objects.requireNonNullElse(u.getNome(), ""));
        row.put("email",       Objects.requireNonNullElse(u.getEmail(), ""));
        row.put("telefone",    Objects.requireNonNullElse(u.getTelefone(), ""));
        row.put("cpf",         perfil != null && perfil.getCpf() != null ? perfil.getCpf() : "");
        row.put("cidade",      perfil != null && perfil.getCidade() != null ? perfil.getCidade() : "");
        row.put("estado",      perfil != null && perfil.getEstado() != null ? perfil.getEstado() : "");
        row.put("grupo",       perfil != null && perfil.getGrupoCliente() != null && perfil.getGrupoCliente().getDescricao() != null
                ? perfil.getGrupoCliente().getDescricao() : "");
        row.put("ativo",       Objects.requireNonNullElse(u.getAtivo(), false));
        row.put("emailVerificado", Objects.requireNonNullElse(u.getEmailVerificado(), false));

        return row;
    }

}