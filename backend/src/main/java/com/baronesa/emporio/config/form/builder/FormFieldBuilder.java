package com.baronesa.emporio.config.form.builder;

import com.baronesa.emporio.config.form.enums.FormFieldType;
import com.baronesa.emporio.config.i18n.MessageResolver;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FormFieldBuilder {
    private String name;
    private String label;
    private String labelI18nKey;
    private FormFieldType type;
    private String placeholder;
    private String placeholderI18nKey;
    private boolean required = false;
    private boolean autofocus = false;
    private boolean showform = true;
    private int order = 0;
    private String errorMessage;
    private String errorMessageI18nKey;
    private Map<String, Object> props = new HashMap<>();
    private List<String> validations = new ArrayList<>();
    private String customIcon;
    private String customCols;
    private Integer customWidth;
    private MessageResolver messageResolver;

    public static FormFieldBuilder create(String name, String label, FormFieldType type) {
        FormFieldBuilder builder = new FormFieldBuilder();
        builder.name = name;
        builder.label = label;
        builder.type = type;
        return builder;
    }

    public static FormFieldBuilder create(String name, String labelI18nKey, FormFieldType type, MessageResolver messageResolver) {
        FormFieldBuilder builder = new FormFieldBuilder();
        builder.name = name;
        builder.labelI18nKey = labelI18nKey;
        builder.type = type;
        builder.messageResolver = messageResolver;
        return builder;
    }

    public FormFieldBuilder placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public FormFieldBuilder placeholderI18n(String placeholderI18nKey) {
        this.placeholderI18nKey = placeholderI18nKey;
        return this;
    }

    public FormFieldBuilder required(boolean required) {
        this.required = required;
        return this;
    }

    public FormFieldBuilder required(String errorMessage) {
        this.required = true;
        this.errorMessage = errorMessage;
        return this;
    }

    public FormFieldBuilder requiredI18n(String errorMessageI18nKey) {
        this.required = true;
        this.errorMessageI18nKey = errorMessageI18nKey;
        return this;
    }

    public FormFieldBuilder autofocus() {
        this.autofocus = true;
        return this;
    }

    public FormFieldBuilder order(int order) {
        this.order = order;
        return this;
    }

    public FormFieldBuilder showform(boolean showform) {
        this.showform = showform;
        return this;
    }

    public FormFieldBuilder prop(String key, Object value) {
        this.props.put(key, value);
        return this;
    }

    public FormFieldBuilder propI18n(String key, String i18nKey) {
        if (messageResolver != null) {
            this.props.put(key, messageResolver.getMessage(i18nKey));
        }
        return this;
    }

    public FormFieldBuilder validation(String validation) {
        this.validations.add(validation);
        return this;
    }

    public FormFieldBuilder icon(String icon) {
        this.customIcon = icon;
        return this;
    }

    public FormFieldBuilder cols(String cols) {
        this.customCols = cols;
        return this;
    }

    public FormFieldBuilder width(int width) {
        this.customWidth = width;
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> field = new LinkedHashMap<>();

        field.put("name", name);

        // Resolve label
        if (messageResolver != null && labelI18nKey != null) {
            field.put("label", messageResolver.getMessage(labelI18nKey));
        } else {
            field.put("label", label);
        }

        field.put("component", type.getComponent());
        field.put("icon", customIcon != null ? customIcon : type.getIcon());
        field.put("cols", customCols != null ? customCols : type.getCols());
        field.put("width", customWidth != null ? customWidth : type.getWidth());
        field.put("sortable", type.isSortable());
        field.put("show", type.isShow());
        field.put("showform", showform);
        field.put("order", order);
        field.put("required", required);
        field.put("autofocus", autofocus);
        field.put("props", props);
        field.put("validations", validations);

        // Resolve placeholder
        if (messageResolver != null && placeholderI18nKey != null) {
            field.put("placeholder", messageResolver.getMessage(placeholderI18nKey));
        } else if (placeholder != null) {
            field.put("placeholder", placeholder);
        }

        // Resolve error message
        if (messageResolver != null && errorMessageI18nKey != null) {
            field.put("errormessage", messageResolver.getMessage(errorMessageI18nKey));
        } else if (errorMessage != null) {
            field.put("errormessage", errorMessage);
        }

        return field;
    }
}
