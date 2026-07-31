package com.baronesa.emporio.dynamicform.config;

import com.baronesa.emporio.config.form.base.ActionConfigurable;
import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.form.base.DynamicFormConfigurable;
import com.baronesa.emporio.config.form.base.FrontendFormConfigurable;
import com.baronesa.emporio.dynamicform.entity.DynamicFormDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementação de BaseFormConfig que lê configurações do banco de dados
 * com compatibilidade para o formato esperado pelo frontend
 */
@Slf4j
public class DynamicFormConfig extends BaseFormConfig
        implements FrontendFormConfigurable, ActionConfigurable, DynamicFormConfigurable {

    private final DynamicFormDefinition definition;

    public DynamicFormConfig(DynamicFormDefinition definition) {
        this.definition = definition;
    }

    @Override
    public String getProgramName() {
        return definition.getProgramName();
    }

    @Override
    public String getProgramIcon() {
        return definition.getProgramIcon();
    }

    @Override
    public String getEntityType() {
        return definition.getEntityType();
    }

    @Override
    protected String getTableOrder() {
        return definition.getTableOrder() != null ? definition.getTableOrder() : "id";
    }

    @Override
    public Object getFrontendFormDefinitions() {
        Map<String, Object> formStructure = definition.getFormStructure();

        if (formStructure != null && formStructure.containsKey("tabs")) {
            // CORREÇÃO: Retornar a estrutura completa com tabs, não apenas os campos
            Map<String, Object> tabGroup = new LinkedHashMap<>();
            tabGroup.put("type", "tab-group");

            // Processar cada tab
            List<Map<String, Object>> tabs = (List<Map<String, Object>>) formStructure.get("tabs");
            List<Map<String, Object>> processedTabs = new ArrayList<>();

            for (Map<String, Object> tab : tabs) {
                Map<String, Object> processedTab = new LinkedHashMap<>(tab);

                // Se a tab tem um componente customizado, não processar os campos
                if (tab.containsKey("component") && tab.get("component") != null) {
                    // Tab com componente customizado - manter como está
                    processedTabs.add(processedTab);
                } else if (tab.containsKey("fields")) {
                    // Tab com campos normais - processar cada campo
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) tab.get("fields");
                    List<Map<String, Object>> processedFields = new ArrayList<>();

                    for (Map<String, Object> field : fields) {
                        // Usar o FieldTypeMapper apenas se o campo não tiver component definido
                        if (field.containsKey("component") && field.get("component") != null) {
                            // Campo com componente customizado - manter como está
                            processedFields.add(new LinkedHashMap<>(field));
                        } else {
                            // Campo normal - aplicar mapeamento
                            processedFields.add(FieldTypeMapper.mapField(field, definition.getEntityType()));
                        }
                    }

                    processedTab.put("fields", processedFields);
                    processedTabs.add(processedTab);
                } else {
                    // Tab sem campos nem componente - manter como está
                    processedTabs.add(processedTab);
                }
            }

            tabGroup.put("tabs", processedTabs);
            return tabGroup;
        }

        // Se não tem estrutura de tabs, retornar estrutura vazia
        return Map.of("type", "tab-group", "tabs", List.of());
    }

    @Override
    public List<Map<String, Object>> getFormActions() {
        Map<String, Object> formStructure = definition.getFormStructure();
        if (formStructure != null && formStructure.containsKey("actions")) {
            List<Map<String, Object>> actions = (List<Map<String, Object>>) formStructure.get("actions");

            log.debug("Total de ações no banco: {}", actions.size());

            List<Map<String, Object>> convertedActions = new ArrayList<>();

            for (Map<String, Object> action : actions) {
                log.debug("Processando ação: type={}, label={}", action.get("type"), action.get("label"));
                try {
                    Map<String, Object> converted = convertActionToFrontendFormat(action);
                    convertedActions.add(converted);
                    log.debug("Ação convertida com sucesso: {}", converted);
                } catch (Exception e) {
                    log.error("Erro ao converter ação: {}", action, e);
                }
            }

            log.debug("Total de ações convertidas: {}", convertedActions.size());

            return convertedActions;
        }
        return List.of();
    }

    /**
     * Converte o formato de ação do banco para o formato esperado pelo frontend
     */
    private Map<String, Object> convertActionToFrontendFormat(Map<String, Object> dbAction) {
        Map<String, Object> frontendAction = new LinkedHashMap<>();

        // Mapear 'type' para 'action'
        String type = (String) dbAction.get("type");
        frontendAction.put("action", type != null ? type.toLowerCase() : "custom");

        // Campos diretos - só adicionar se não for null
        if (dbAction.get("label") != null) {
            frontendAction.put("label", dbAction.get("label"));
        }
        if (dbAction.get("icon") != null) {
            frontendAction.put("icon", dbAction.get("icon"));
        }

        // Adicionar color baseado no tipo
        if ("DELETE".equalsIgnoreCase(type)) {
            frontendAction.put("color", "negative");
        } else if ("ADD".equalsIgnoreCase(type) || "CREATE".equalsIgnoreCase(type)) {
            frontendAction.put("color", "primary");
        } else if (dbAction.containsKey("color") && dbAction.get("color") != null) {
            frontendAction.put("color", dbAction.get("color"));
        }

        // Mapear propriedades booleanas
        if (dbAction.containsKey("onDoubleClick") && dbAction.get("onDoubleClick") != null) {
            frontendAction.put("onDoubleClick", dbAction.get("onDoubleClick"));
        }

        if (dbAction.containsKey("requiresSelection") && dbAction.get("requiresSelection") != null) {
            frontendAction.put("requiresSelection", dbAction.get("requiresSelection"));
        }

        if (dbAction.containsKey("inlineOnly") && dbAction.get("inlineOnly") != null) {
            frontendAction.put("inlineOnly", dbAction.get("inlineOnly"));
        }

        // Mapear confirmação - só criar o Map se ambos os valores existirem
        String confirmTitle = (String) dbAction.get("confirmTitle");
        String confirmMessage = (String) dbAction.get("confirmMessage");
        if (confirmTitle != null && confirmMessage != null) {
            Map<String, String> confirm = new LinkedHashMap<>();
            confirm.put("title", confirmTitle);
            confirm.put("message", confirmMessage);
            frontendAction.put("confirm", confirm);
        }

        // Mapear propriedades de diálogo
        if (dbAction.containsKey("opensDialog") && dbAction.get("opensDialog") != null) {
            frontendAction.put("opensDialog", dbAction.get("opensDialog"));
        }

        if (dbAction.containsKey("dialogComponent") && dbAction.get("dialogComponent") != null) {
            frontendAction.put("dialogComponent", dbAction.get("dialogComponent"));
        }

        // Mapear endpoint e método
        if (dbAction.containsKey("endpoint") && dbAction.get("endpoint") != null) {
            frontendAction.put("endpoint", dbAction.get("endpoint"));
        }

        if (dbAction.containsKey("method") && dbAction.get("method") != null) {
            frontendAction.put("method", dbAction.get("method"));
        }

        // Mapear mensagem de sucesso
        if (dbAction.containsKey("successMessage") && dbAction.get("successMessage") != null) {
            frontendAction.put("successMessage", dbAction.get("successMessage"));
        }

        // Mapear condição
        if (dbAction.containsKey("condition") && dbAction.get("condition") != null) {
            frontendAction.put("condition", dbAction.get("condition"));
        }

        // Mapear nome para ações customizadas
        if (dbAction.containsKey("name") && dbAction.get("name") != null) {
            frontendAction.put("name", dbAction.get("name"));
        }

        // Copiar quaisquer props adicionais
        if (dbAction.containsKey("props") && dbAction.get("props") != null) {
            frontendAction.put("props", dbAction.get("props"));
        }

        if (dbAction.containsKey("reloadAfterSuccess") && dbAction.get("reloadAfterSuccess") != null) {
            frontendAction.put("reloadAfterSuccess", dbAction.get("reloadAfterSuccess"));
        }

        // ADICIONAR SUPORTE PARA CAMPOS DE NAVEGAÇÃO
        if (dbAction.containsKey("route") && dbAction.get("route") != null) {
            frontendAction.put("route", dbAction.get("route"));
        }

        if (dbAction.containsKey("openInNewTab") && dbAction.get("openInNewTab") != null) {
            frontendAction.put("openInNewTab", dbAction.get("openInNewTab"));
        }

        if (dbAction.containsKey("query") && dbAction.get("query") != null) {
            frontendAction.put("query", dbAction.get("query"));
        }

        if (dbAction.containsKey("params") && dbAction.get("params") != null) {
            frontendAction.put("params", dbAction.get("params"));
        }

        return frontendAction;
    }

    @Override
    public List<Map<String, Object>> getTableColumns() {
        Map<String, Object> tableColumns = definition.getTableColumns();
        if (tableColumns != null && tableColumns.containsKey("columns")) {
            return (List<Map<String, Object>>) tableColumns.get("columns");
        }
        return List.of();
    }

    @Override
    public List<Map<String, Object>> getFormDefinitions() {
        // Método legado - retorna lista vazia
        return List.of();
    }

    // Método adicional para obter custom slots
    public Map<String, Object> getCustomSlots() {
        return definition.getCustomSlots() != null ? definition.getCustomSlots() : Map.of();
    }

    // Método adicional para obter configurações do diálogo
    public Map<String, Object> getDialogConfig() {
        return definition.getDialogConfig() != null ? definition.getDialogConfig() : Map.of(
            "width", "800px",
            "maxWidth", "95vw",
            "maxHeight", "90vh",
            "fullscreenMobile", true
        );
    }
}