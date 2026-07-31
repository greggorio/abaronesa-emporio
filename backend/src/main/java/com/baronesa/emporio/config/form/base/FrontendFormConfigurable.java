package com.baronesa.emporio.config.form.base;

import java.util.List;
import java.util.Map;

/**
 * Interface para classes que fornecem definições de formulário no formato do frontend moderno.
 * Complementa a interface FormConfigurable existente.
 */
public interface FrontendFormConfigurable {

    /**
     * Retorna as definições de formulário no novo formato esperado pelo GenericFormDialog.vue
     *
     * Para formulários simples, retorna List<Map<String, Object>>
     * Para formulários com tabs, retorna Map<String, Object> com estrutura type: "tab-group"
     *
     * @return Definições de campos ou estrutura de tabs
     */
    default Object getFrontendFormDefinitions() {
        // Por padrão, retorna lista simples para manter compatibilidade
        return getFrontendFormDefinitionsList();
    }

    /**
     * Método legado para formulários simples (sem tabs)
     * @deprecated Use getFrontendFormDefinitions() que suporta tanto lista quanto tabs
     */
    @Deprecated
    default List<Map<String, Object>> getFrontendFormDefinitionsList() {
        return List.of();
    }

    /**
     * Retorna o tipo de entidade (usado para construir paths de upload)
     * Ex: "categorias", "subcategorias", "produtos"
     *
     * @return Nome da entidade no plural e minúsculo
     */
    default String getEntityType() {
        String className = this.getClass().getSimpleName();
        // Remove "FormConfig" e converte para minúsculo
        String entity = className.replace("FormConfig", "").toLowerCase();

        // Adiciona 's' para plural (regra simples)
        // TODO: Implementar pluralização mais sofisticada se necessário
        return entity + "s";
    }

    /**
     * Retorna o endpoint base para operações da entidade
     *
     * @return Endpoint base (ex: "/api/categorias")
     */
    default String getApiEndpoint() {
        return "/api/" + getEntityType();
    }
}