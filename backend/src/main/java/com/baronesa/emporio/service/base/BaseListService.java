package com.baronesa.emporio.service.base;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.util.FilterSpecificationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RequiredArgsConstructor
public abstract class BaseListService<T> {

    /** Repositório com suporte a Specification */
    protected abstract JpaSpecificationExecutor<T> getRepository();

    /** Classe da entidade usada no FilterSpecificationBuilder */
    protected abstract Class<T> getEntityClass();

    /** Config de formulário/tabela específica da entidade */
    protected abstract BaseFormConfig getFormConfig(MessageResolver resolver);

    /** Converte a entidade em um Map (linha da tabela) */
    protected abstract Map<String, Object> entityToRow(T entity);

    /**
     * Mapeamento de campos de UI para propriedades JPA.
     * Permite mapear campos como "categoria" → "categoria.nome" para ordenação em relacionamentos.
     * @return Map com mapeamentos (campo_ui → caminho_jpa)
     */
    protected Map<String, String> getFieldMappings() {
        return Map.of();
    }

    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––

    public Map<String, Object> list(int pagina,
                                    int tamanho,
                                    String ordenacao,
                                    String direcao,
                                    String filtroJson,
                                    MessageResolver resolver) {

        // Mapear campo de UI para propriedade JPA se necessário
        String sortField = ordenacao;
        if (ordenacao != null && !ordenacao.isBlank()) {
            sortField = getFieldMappings().getOrDefault(ordenacao, ordenacao);
        }

        Sort sort = (sortField != null && !sortField.isBlank())
                ? Sort.by(Sort.Direction.fromString(direcao), sortField)
                : Sort.unsorted();

        Pageable pageable = PageRequest.of(pagina, tamanho, sort);

        Specification<T> spec = new FilterSpecificationBuilder<>(getEntityClass(), getFieldMappings())
                .build(filtroJson);

        Page<T> page = getRepository().findAll(spec, pageable);

        List<Map<String, Object>> tableData = page.getContent().stream()
                .map(this::entityToRow)
                .toList();

        // Criar resposta base
        Map<String, Object> response = new HashMap<>();

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

        // Adicionar metadado de ordenação (reflete a ordenação real aplicada)
        if (ordenacao != null && !ordenacao.isBlank()) {
            String tableOrder = ordenacao + " " + direcao.toUpperCase();
            response.put("table_order", tableOrder);
        }

        return response;
    }
}
