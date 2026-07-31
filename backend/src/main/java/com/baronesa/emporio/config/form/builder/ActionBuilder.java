package com.baronesa.emporio.config.form.builder;

import com.baronesa.emporio.config.form.enums.ActionType;
import com.baronesa.emporio.config.i18n.MessageResolver;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ActionBuilder {
    private ActionType type;
    private String action;
    private String label;
    private String labelI18nKey;
    private String icon;
    private String color;
    private Boolean requiresSelection;
    private Boolean inlineOnly;
    private boolean onDoubleClick = false;
    private String handler;
    private String endpoint;
    private String method = "POST";
    private String component;
    private Map<String, Object> props = new HashMap<>();
    private String successMessage;
    private String successMessageI18nKey;
    private Map<String, Object> confirm;
    private MessageResolver messageResolver;
    private boolean opensDialog = false;
    private String dialogComponent;
    private boolean reloadAfterSuccess = false;
    private Map<String, Object> conditions = new LinkedHashMap<>();

    // NOVAS PROPRIEDADES PARA NAVEGAÇÃO
    private String route;
    private boolean openInNewTab = false;
    private Map<String, Object> query = new HashMap<>();
    private Map<String, Object> params = new HashMap<>();

    public ActionBuilder condition(String field, Object expectedValue) {
        this.conditions.put(field, expectedValue);
        return this;
    }

    public ActionBuilder reloadAfterSuccess(boolean reload) {
        this.reloadAfterSuccess = reload;
        return this;
    }

    public ActionBuilder opensDialog(boolean opensDialog) {
        this.opensDialog = opensDialog;
        return this;
    }

    public ActionBuilder dialogComponent(String dialogComponent) {
        this.dialogComponent = dialogComponent;
        return this;
    }

    // NOVOS MÉTODOS PARA NAVEGAÇÃO
    public ActionBuilder route(String route) {
        this.route = route;
        return this;
    }

    public ActionBuilder openInNewTab(boolean openInNewTab) {
        this.openInNewTab = openInNewTab;
        return this;
    }

    public ActionBuilder query(String key, Object value) {
        this.query.put(key, value);
        return this;
    }

    public ActionBuilder queries(Map<String, Object> queries) {
        this.query.putAll(queries);
        return this;
    }

    public ActionBuilder param(String key, Object value) {
        this.params.put(key, value);
        return this;
    }

    public ActionBuilder params(Map<String, Object> params) {
        this.params.putAll(params);
        return this;
    }

    // MÉTODO HELPER PARA CRIAR AÇÃO DE NAVEGAÇÃO
    public static ActionBuilder navigate(String route, String label) {
        ActionBuilder builder = new ActionBuilder();
        builder.type = ActionType.NAVIGATE; // Assumindo que você adicionará NAVIGATE ao enum
        builder.action = "navigate";
        builder.route = route;
        builder.label = label;
        builder.icon = "visibility"; // ícone padrão para navegação
        return builder;
    }

    public static ActionBuilder navigateI18n(String route, String labelI18nKey, MessageResolver messageResolver) {
        ActionBuilder builder = new ActionBuilder();
        builder.type = ActionType.NAVIGATE;
        builder.action = "navigate";
        builder.route = route;
        builder.labelI18nKey = labelI18nKey;
        builder.messageResolver = messageResolver;
        builder.icon = "visibility";
        return builder;
    }

    public static ActionBuilder create(ActionType type, String label) {
        ActionBuilder builder = new ActionBuilder();
        builder.type = type;
        builder.action = type.getAction();
        builder.label = label;
        builder.icon = type.getDefaultIcon();
        builder.color = type.getDefaultColor();
        builder.requiresSelection = type.isRequiresSelection();
        builder.inlineOnly = type.isInlineOnly();
        return builder;
    }

    public static ActionBuilder create(ActionType type, String labelI18nKey, MessageResolver messageResolver) {
        ActionBuilder builder = new ActionBuilder();
        builder.type = type;
        builder.action = type.getAction();
        builder.labelI18nKey = labelI18nKey;
        builder.icon = type.getDefaultIcon();
        builder.color = type.getDefaultColor();
        builder.requiresSelection = type.isRequiresSelection();
        builder.inlineOnly = type.isInlineOnly();
        builder.messageResolver = messageResolver;
        return builder;
    }

    public static ActionBuilder custom(String action, String label) {
        ActionBuilder builder = new ActionBuilder();
        builder.type = ActionType.CUSTOM;
        builder.action = action;
        builder.label = label;
        return builder;
    }

    public ActionBuilder icon(String icon) {
        this.icon = icon;
        return this;
    }

    public ActionBuilder color(String color) {
        this.color = color;
        return this;
    }

    public ActionBuilder requiresSelection(boolean requiresSelection) {
        this.requiresSelection = requiresSelection;
        return this;
    }

    public ActionBuilder inlineOnly(boolean inlineOnly) {
        this.inlineOnly = inlineOnly;
        return this;
    }

    public ActionBuilder onDoubleClick() {
        this.onDoubleClick = true;
        return this;
    }

    public ActionBuilder handler(String handler) {
        this.handler = handler;
        return this;
    }

    public ActionBuilder endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public ActionBuilder method(String method) {
        this.method = method;
        return this;
    }

    public ActionBuilder component(String component) {
        this.component = component;
        return this;
    }

    public ActionBuilder prop(String key, Object value) {
        this.props.put(key, value);
        return this;
    }

    public ActionBuilder successMessage(String message) {
        this.successMessage = message;
        return this;
    }

    public ActionBuilder successMessageI18n(String messageI18nKey) {
        this.successMessageI18nKey = messageI18nKey;
        return this;
    }

    public ActionBuilder confirm(String title, String message) {
        this.confirm = new LinkedHashMap<>();
        this.confirm.put("title", title);
        this.confirm.put("message", message);
        return this;
    }

    public ActionBuilder confirmI18n(String titleI18nKey, String messageI18nKey) {
        this.confirm = new LinkedHashMap<>();
        if (messageResolver != null) {
            this.confirm.put("title", messageResolver.getMessage(titleI18nKey));
            this.confirm.put("message", messageResolver.getMessage(messageI18nKey));
        } else {
            this.confirm.put("title", titleI18nKey);
            this.confirm.put("message", messageI18nKey);
        }
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> actionDef = new LinkedHashMap<>();

        // ADICIONAR TYPE SEMPRE (necessário para o frontend)
        actionDef.put("type", type != null ? type.name() : "CUSTOM");

        actionDef.put("action", action);
        actionDef.put("icon", icon);

        // Resolve label
        if (messageResolver != null && labelI18nKey != null) {
            actionDef.put("label", messageResolver.getMessage(labelI18nKey));
        } else {
            actionDef.put("label", label);
        }

        if (color != null) {
            actionDef.put("color", color);
        }

        // ADICIONAR PROPRIEDADES DE NAVEGAÇÃO
        if (route != null) {
            actionDef.put("route", route);
        }

        if (openInNewTab) {
            actionDef.put("openInNewTab", true);
        }

        if (!query.isEmpty()) {
            actionDef.put("query", query);
        }

        if (!params.isEmpty()) {
            actionDef.put("params", params);
        }

        if (opensDialog) {
            actionDef.put("opensDialog", true);
        }

        if (dialogComponent != null) {
            actionDef.put("dialogComponent", dialogComponent);
        }

        if (reloadAfterSuccess) {
            actionDef.put("reloadAfterSuccess", true);
        }

        if (requiresSelection != null) {
            actionDef.put("requiresSelection", requiresSelection);
        }

        if (inlineOnly != null) {
            actionDef.put("inlineOnly", inlineOnly);
        }

        if (onDoubleClick) {
            actionDef.put("onDoubleClick", true);
        }

        if (handler != null) {
            actionDef.put("handler", handler);
        }

        if (endpoint != null) {
            actionDef.put("endpoint", endpoint);
            actionDef.put("method", method);
        }

        if (component != null) {
            actionDef.put("component", component);
        }

        if (!props.isEmpty()) {
            actionDef.put("props", props);
        }

        if (messageResolver != null && successMessageI18nKey != null) {
            actionDef.put("successMessage", messageResolver.getMessage(successMessageI18nKey));
        } else if (successMessage != null) {
            actionDef.put("successMessage", successMessage);
        }

        if (confirm != null) {
            actionDef.put("confirm", confirm);
        }

        if (!conditions.isEmpty()) {
            actionDef.put("conditions", conditions);
        }

        return actionDef;
    }
}