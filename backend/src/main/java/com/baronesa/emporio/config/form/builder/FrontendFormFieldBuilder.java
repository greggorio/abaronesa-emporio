package com.baronesa.emporio.config.form.builder;

import com.baronesa.emporio.config.form.enums.FormFieldType;
import com.baronesa.emporio.config.i18n.MessageResolver;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Builder para criar definições de formulário no formato esperado pelo frontend moderno.
 * Gera estrutura compatível com o GenericFormDialog.vue
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FrontendFormFieldBuilder {
    private String name;
    private String label;
    private String labelI18nKey;
    private FormFieldType type;
    private String placeholder;
    private String placeholderI18nKey;
    private boolean required = false;
    private String cols;
    private List<String> validations = new ArrayList<>();
    private Map<String, Object> props = new HashMap<>();
    private MessageResolver messageResolver;
    private boolean readOnly = false;

    // Campos específicos do frontend
    private boolean immediateUpload = false;
    private String uploadEndpoint;
    private String validationPreset;
    private Map<String, Object> uploadOptions = new HashMap<>();
    private String accept;
    private Long maxSize;
    private String optionsEndpoint;
    private List<Map<String, Object>> options; // ADICIONADO: para opções estáticas
    private String dependsOn;
    private String dependsOnEndpoint;
    private String entityType;
    private String mediaBasePath;  // NOVO CAMPO
    private Boolean showOnEdit; // Para controlar visibilidade apenas em modo edição

    public static FrontendFormFieldBuilder create(String name, String label, FormFieldType type) {
        FrontendFormFieldBuilder builder = new FrontendFormFieldBuilder();
        builder.name = name;
        builder.label = label;
        builder.type = type;
        builder.cols = type.getCols(); // usa o padrão do enum

        // NOVO: Aplicar props padrão do tipo
        Map<String, Object> defaultProps = type.getDefaultProps();
        if (defaultProps != null && !defaultProps.isEmpty()) {
            builder.props.putAll(defaultProps);
        }

        return builder;
    }

    public FrontendFormFieldBuilder readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public FrontendFormFieldBuilder readOnly() {
        this.readOnly = true;
        return this;
    }

    public static FrontendFormFieldBuilder create(String name, String labelI18nKey, FormFieldType type, MessageResolver messageResolver) {
        FrontendFormFieldBuilder builder = new FrontendFormFieldBuilder();
        builder.name = name;
        builder.labelI18nKey = labelI18nKey;
        builder.type = type;
        builder.messageResolver = messageResolver;
        builder.cols = type.getCols(); // usa o padrão do enum

        // NOVO: Aplicar props padrão do tipo
        Map<String, Object> defaultProps = type.getDefaultProps();
        if (defaultProps != null && !defaultProps.isEmpty()) {
            builder.props.putAll(defaultProps);
        }

        return builder;
    }

    public FrontendFormFieldBuilder placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public FrontendFormFieldBuilder placeholderI18n(String placeholderI18nKey) {
        this.placeholderI18nKey = placeholderI18nKey;
        return this;
    }

    public FrontendFormFieldBuilder required() {
        this.required = true;
        this.validations.add("required");
        return this;
    }

    public FrontendFormFieldBuilder cols(String cols) {
        // Simplifica para o formato do frontend (apenas col-12, col-6, etc)
        this.cols = extractSimpleCols(cols);
        return this;
    }

    public FrontendFormFieldBuilder validation(String validation) {
        this.validations.add(validation);
        return this;
    }

    public FrontendFormFieldBuilder validations(List<String> validations) {
        if (validations != null) {
            this.validations.addAll(validations);
        }
        return this;
    }

    public FrontendFormFieldBuilder prop(String key, Object value) {
        this.props.put(key, value);
        return this;
    }

    // NOVO: Método para adicionar opções estáticas (para SELECT, RADIO, etc)
    public FrontendFormFieldBuilder options(List<?> optionsList) {
        this.options = new ArrayList<>();
        for (Object option : optionsList) {
            if (option instanceof Map) {
                this.options.add((Map<String, Object>) option);
            } else if (option instanceof String) {
                // Converte string simples para formato label/value
                Map<String, Object> optionMap = new LinkedHashMap<>();
                optionMap.put("label", option);
                optionMap.put("value", option);
                this.options.add(optionMap);
            }
        }
        return this;
    }

    // Métodos específicos para upload de arquivo
    public FrontendFormFieldBuilder immediateUpload() {
        this.immediateUpload = true;
        return this;
    }

    public FrontendFormFieldBuilder uploadEndpoint(String endpoint) {
        this.uploadEndpoint = endpoint;
        this.immediateUpload = true; // upload endpoint implica em immediate upload
        return this;
    }

    public FrontendFormFieldBuilder entityType(String entityType) {
        this.entityType = entityType;
        // Gera automaticamente o mediaBasePath se não foi definido
        if (this.mediaBasePath == null) {
            this.mediaBasePath = "/media/" + entityType + "/";
        }
        return this;
    }

    // NOVO MÉTODO
    public FrontendFormFieldBuilder mediaBasePath(String path) {
        this.mediaBasePath = path;
        return this;
    }

    public FrontendFormFieldBuilder validationPreset(String preset) {
        this.validationPreset = preset;
        return this;
    }

    public FrontendFormFieldBuilder fileFieldName(String fieldName) {
        this.uploadOptions.put("fileFieldName", fieldName);
        return this;
    }

    public FrontendFormFieldBuilder uploadExtraField(String key, Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> extraFields = (Map<String, Object>) uploadOptions.computeIfAbsent("extraFields", k -> new HashMap<>());
        extraFields.put(key, value);
        return this;
    }

    public FrontendFormFieldBuilder accept(String accept) {
        this.accept = accept;
        return this;
    }

    public FrontendFormFieldBuilder maxSize(long bytes) {
        this.maxSize = bytes;
        return this;
    }

    // Métodos para campos dependentes
    public FrontendFormFieldBuilder optionsEndpoint(String endpoint) {
        this.optionsEndpoint = endpoint;
        return this;
    }

    public FrontendFormFieldBuilder dependsOn(String fieldName) {
        this.dependsOn = fieldName;
        return this;
    }

    public FrontendFormFieldBuilder dependsOnEndpoint(String endpoint) {
        this.dependsOnEndpoint = endpoint;
        return this;
    }

    public FrontendFormFieldBuilder showOnEdit() {
        this.showOnEdit = true;
        return this;
    }

    public FrontendFormFieldBuilder showOnEdit(boolean showOnEdit) {
        this.showOnEdit = showOnEdit;
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> field = new LinkedHashMap<>();

        field.put("name", name);

        // Resolve label com i18n
        if (messageResolver != null && labelI18nKey != null) {
            field.put("label", messageResolver.getMessage(labelI18nKey));
        } else {
            field.put("label", label);
        }

        // Converte nome do componente para minúsculo (exceto QFile)
        field.put("component", convertComponentName(type.getComponent()));

        field.put("cols", cols);
        field.put("required", required);

        // Validações
        if (!validations.isEmpty()) {
            field.put("validations", validations);
        }

        // Placeholder
        if (messageResolver != null && placeholderI18nKey != null) {
            field.put("placeholder", messageResolver.getMessage(placeholderI18nKey));
        } else if (placeholder != null) {
            field.put("placeholder", placeholder);
        }

        // Props adicionais (já inclui as props padrão do tipo)
        if (!props.isEmpty()) {
            field.put("props", props);
        }

        if (readOnly) {
            field.put("readOnly", true);
        }

        // NOVO: Adicionar opções estáticas se existirem
        if (options != null && !options.isEmpty()) {
            field.put("options", options);
        }

        // Adicionar showOnEdit se especificado
        if (showOnEdit != null) {
            field.put("showOnEdit", showOnEdit);
        }

        // Campos específicos para QFile
        if (type == FormFieldType.FILE) {
            if (immediateUpload) {
                field.put("immediateUpload", true);
            }

            if (uploadEndpoint != null) {
                field.put("uploadEndpoint", uploadEndpoint);
            }

            if (entityType != null) {
                field.put("entityType", entityType);
            }

            // NOVO: Adiciona mediaBasePath
            if (mediaBasePath != null) {
                field.put("mediaBasePath", mediaBasePath);
            }

            if (validationPreset != null) {
                field.put("validationPreset", validationPreset);
            }

            if (!uploadOptions.isEmpty()) {
                field.put("uploadOptions", uploadOptions);
            }

            if (accept != null) {
                field.put("accept", accept);
            }

            if (maxSize != null) {
                field.put("maxSize", maxSize);
            }
        }

        // Campos para select/dependentes
        if (type == FormFieldType.SELECT || type == FormFieldType.RADIO) {
            if (optionsEndpoint != null) {
                field.put("optionsEndpoint", optionsEndpoint);
            }

            if (dependsOn != null) {
                field.put("dependsOn", dependsOn);
            }

            if (dependsOnEndpoint != null) {
                field.put("dependsOnEndpoint", dependsOnEndpoint);
            }
        }

        return field;
    }

    /**
     * Converte nomes de componentes para o padrão do frontend
     * ATUALIZADO: Adicionados os novos componentes
     */
    private String convertComponentName(String component) {
        return switch (component) {
            case "QFile" -> "QFile"; // Mantém capitalizado
            case "QInput" -> "q-input";
            case "QSelect" -> "q-select";
            case "QCheckbox" -> "q-checkbox";
            case "QRadio" -> "q-radio-group";
            case "QToggle" -> "q-toggle";
            case "QDate" -> "q-date";
            case "QTime" -> "q-time";
            case "QDatetime" -> "q-datetime";
            case "QTextarea" -> "q-textarea";
            case "QBadge" -> "q-badge"; // NOVO: para BOOLEAN
            default -> component.toLowerCase();
        };
    }

    /**
     * Extrai formato simplificado de colunas (col-12 em vez de col-12 col-sm-12 col-md-12)
     */
    private String extractSimpleCols(String cols) {
        if (cols == null || cols.isEmpty()) return "col-12";

        // Pega apenas a primeira classe col-X
        String[] parts = cols.split(" ");
        for (String part : parts) {
            if (part.startsWith("col-") && !part.contains("sm") && !part.contains("md") && !part.contains("lg")) {
                return part;
            }
        }

        return "col-12"; // default
    }
}
