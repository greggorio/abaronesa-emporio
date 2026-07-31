package com.baronesa.emporio.config.form.base;

import com.baronesa.emporio.config.form.enums.FormFieldType;
import com.baronesa.emporio.config.i18n.MessageResolver;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe‐base para todas as configurações de formulário e tabela.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseFormConfig implements FormConfigurable {

    /* -------- i18n -------------------------------------------------- */
    protected MessageResolver messageResolver;
    public void setMessageResolver(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    /* -------- Gancho obrigatório ------------------------------------ */
    /** Nome do programa (aparece em <code>program_name</code>). */
    @Override
    public abstract String getProgramName();   // agora público

    /** Ícone do programa (novo método abstrato). */
    public abstract String getProgramIcon();

    public abstract String getEntityType();

    /* -------- Métodos de configuração com valores padrão ------------ */

    /**
     * Define a ordenação padrão da tabela.
     * @return String no formato "campo [ASC|DESC]". Padrão: "id"
     */
    protected String getTableOrder() {
        return "id";
    }

    /**
     * Define a direção padrão da ordenação.
     * @return "asc" ou "desc". Padrão: "asc"
     */
    protected String getDefaultSortDirection() {
        // Extrai a direção do getTableOrder() se especificada
        String order = getTableOrder();
        if (order.toLowerCase().endsWith(" desc")) {
            return "desc";
        }
        return "asc";
    }

    /**
     * Define a coluna padrão para ordenação.
     * @return Nome da coluna. Padrão: primeira parte de getTableOrder()
     */
    protected String getDefaultSortColumn() {
        String order = getTableOrder();
        // Remove ASC/DESC se presente
        return order.split("\\s+")[0];
    }

    /**
     * Define a chave primária para as linhas da tabela.
     * @return Nome do campo chave. Padrão: "id"
     */
    protected String getRowKey() {
        return "id";
    }

    /* -------- API pública ------------------------------------------- */
    public Map<String, Object> createResponse(List<Map<String, Object>> tableData) {
        return createBaseResponse(getProgramName(), tableData);
    }

    /* -------- Helpers compartilhados -------------------------------- */
    protected Map<String, Object> createBaseResponse(String programName,
                                                     List<Map<String, Object>> tableData) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("program_name", programName);
        response.put("program_icon", getProgramIcon());
        response.put("descricao", "");
        response.put("table_order", getTableOrder()); // Agora usa o método
        response.put("isDialogo", true);
        response.put("totalElementos", tableData.size());
        response.put("totalPaginas", 1);
        response.put("table_data", tableData);
        response.put("table_definitions", Map.of(
                "columns", getTableColumns(),
                "rowKey", getRowKey(),                          // Agora usa o método
                "defaultSortColumn", getDefaultSortColumn(),    // Agora usa o método
                "defaultSortDirection", getDefaultSortDirection() // Agora usa o método
        ));

        //TODO REMOVER POR COMPLETO response.put("form_definitions", getFormDefinitions());


        // NOVO: Adiciona form_definitions_new se a classe implementar FrontendFormConfigurable
        if (this instanceof FrontendFormConfigurable) {
            FrontendFormConfigurable frontendConfig = (FrontendFormConfigurable) this;
            Object formDefs = frontendConfig.getFrontendFormDefinitions();

            // Se retornou um Map com tabs, adicionar o wrapper type: "tab-group"
            if (formDefs instanceof Map) {
                Map<String, Object> formDefMap = (Map<String, Object>) formDefs;
                if (formDefMap.containsKey("tabs") && !formDefMap.containsKey("type")) {
                    formDefMap.put("type", "tab-group");
                }
                response.put("form_definitions_new", formDefMap);
            } else {
                response.put("form_definitions_new", formDefs);
            }

            //TODO remover após constatar que não faz falta
            response.put("form_definitions", formDefs);
        }

        // Adiciona form_botoes se a classe implementar ActionConfigurable
        if (this instanceof ActionConfigurable) {
            ActionConfigurable actionConfig = (ActionConfigurable) this;
            response.put("form_botoes", actionConfig.getFormActions());
        }

        // Adiciona dialog_config se disponível (para formulários dinâmicos)
        if (this instanceof DynamicFormConfigurable) {
            DynamicFormConfigurable dynamicConfig = (DynamicFormConfigurable) this;
            Map<String, Object> dialogConfig = dynamicConfig.getDialogConfig();
            if (dialogConfig != null && !dialogConfig.isEmpty()) {
                response.put("dialog_config", dialogConfig);
            }
        }

        if (messageResolver != null) {
            response.put("locale", messageResolver.getCurrentLanguage());
            response.put("i18n_metadata", Map.of(
                    "language", messageResolver.getCurrentLanguage(),
                    "country", messageResolver.getCurrentCountry()
            ));
        }

        return response;
    }


    /* -------- Utilidades de colunas --------------------------------- */
    protected Map<String, Object> createTableColumn(String name,
                                                    String label,
                                                    FormFieldType type,
                                                    int order) {

        Map<String, Object> column = new LinkedHashMap<>();
        column.put("customSortable", type.isSortable());
        column.put("name",  name);
        column.put("icon",  type.getIcon());
        column.put("width", type.getWidth());
        column.put("label", label);
        column.put("type",  type.getTableColumnType());
        column.put("align", "left");
        column.put("order", order);
        return column;
    }

    protected Map<String, Object> createTableColumnI18n(String name,
                                                        String labelI18nKey,
                                                        FormFieldType type,
                                                        int order) {

        String label = (messageResolver != null)
                ? messageResolver.getMessage(labelI18nKey)
                : labelI18nKey;

        return createTableColumn(name, label, type, order);
    }
}