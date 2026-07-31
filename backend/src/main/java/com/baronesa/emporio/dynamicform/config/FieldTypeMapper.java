package com.baronesa.emporio.dynamicform.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapeia campos do formato do banco para o formato esperado pelo frontend
 */
public class FieldTypeMapper {

    /**
     * Converte um campo do banco para o formato esperado pelo frontend
     *
     * @param dbField campo do banco (tabs[].fields[])
     * @param entityType tipo da entidade (ex: "categorias", "produtos")
     * @return mapa normalizado para o frontend
     */
    public static Map<String, Object> mapField(Map<String, Object> dbField, String entityType) {
        Map<String, Object> frontendField = new HashMap<>(dbField);

        String fieldName = (String) dbField.get("name");
        String fieldType = (String) dbField.get("type");

        // Garante que "cols" exista
        frontendField.putIfAbsent("cols", "col-12");

        // Garante que "required" venha como boolean explícito
        if (dbField.containsKey("required")) {
            frontendField.put("required", dbField.get("required"));
        }

        // Garante que validations venha (vazio se null)
        frontendField.putIfAbsent("validations", List.of());

        // Garante que showOnEdit seja preservado se existir
        if (dbField.containsKey("showOnEdit")) {
            frontendField.put("showOnEdit", dbField.get("showOnEdit"));
        }

        // Mapeia campos de mídia (cover, imagem, foto)
        if (isMediaField(fieldName)) {
            applyMediaUpload(frontendField, entityType, fieldName);
            frontendField.put("type", "FILE");
            return frontendField;
        }

        // DATETIME
        if ("DATETIME".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "q-datetime");
            frontendField.put("type", "DATETIME");

            Map<String, Object> props = getOrCreateProps(frontendField);
            props.putIfAbsent("format24h", true);
            props.putIfAbsent("with-seconds", false);
            props.putIfAbsent("mask", "####-##-## ##:##");
            return frontendField;
        }

        // DATE
        if ("DATE".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "q-date");
            frontendField.put("type", "DATE");

            Map<String, Object> props = getOrCreateProps(frontendField);
            props.putIfAbsent("mask", "##/##/####");
            return frontendField;
        }

        // TIME
        if ("TIME".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "q-time");
            frontendField.put("type", "TIME");

            Map<String, Object> props = getOrCreateProps(frontendField);
            props.putIfAbsent("format24h", true);
            return frontendField;
        }

        // COMPUTED
        if ("COMPUTED".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "ComputedField");
            frontendField.put("fieldType", "computed");
            frontendField.put("type", "COMPUTED");

            // Garantir que props existe
            Map<String, Object> props = getOrCreateProps(frontendField);
            props.putIfAbsent("readonly", true);
            props.putIfAbsent("filled", true);

            return frontendField;
        }

        // TABLE
        if ("TABLE".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "TableField");
            frontendField.put("fieldType", "table");
            frontendField.put("type", "TABLE");
            return frontendField;
        }

        // LOOKUP
        if ("LOOKUP".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "LookupSelect");
            frontendField.put("fieldType", "lookup");
            frontendField.put("type", "LOOKUP");

            Map<String, Object> props = getOrCreateProps(frontendField);
            props.putIfAbsent("option-label", "label");
            props.putIfAbsent("option-value", "id");

            return frontendField;
        }

        // SELECT
        if ("SELECT".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "q-select");
            frontendField.put("type", "SELECT");

            Map<String, Object> props = getOrCreateProps(frontendField);

            // Se tem optionsEndpoint, adicionar props padrão
            if (dbField.containsKey("optionsEndpoint")) {
                props.putIfAbsent("option-label", "label");
                props.putIfAbsent("option-value", "value");
                props.putIfAbsent("emit-value", true);
                props.putIfAbsent("map-options", true);
            }

            return frontendField;
        }

        // Textarea
        if ("TEXTAREA".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "q-input");
            frontendField.put("type", "TEXTAREA");

            Map<String, Object> props = getOrCreateProps(frontendField);
            props.putIfAbsent("type", "textarea");
            props.putIfAbsent("rows", 3);
            return frontendField;
        }

        // Checkbox / Boolean
        if ("CHECKBOX".equalsIgnoreCase(fieldType) || "BOOLEAN".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "q-checkbox");
            frontendField.put("type", "BOOLEAN");
            return frontendField;
        }

        // CURRENCY
        if ("CURRENCY".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "q-input");
            frontendField.put("type", "CURRENCY");

            Map<String, Object> props = getOrCreateProps(frontendField);
            props.putIfAbsent("type", "number");
            props.putIfAbsent("prefix", "R$");
            return frontendField;
        }

        // Campo de texto padrão
        if ("TEXT".equalsIgnoreCase(fieldType) || fieldType == null) {
            frontendField.putIfAbsent("component", "q-input");
            frontendField.put("type", "TEXT");
            return frontendField;
        }

        // Campo de número
        if ("NUMBER".equalsIgnoreCase(fieldType) || "INT".equalsIgnoreCase(fieldType)
                || "LONG".equalsIgnoreCase(fieldType) || "DOUBLE".equalsIgnoreCase(fieldType)) {
            frontendField.put("component", "q-input");
            frontendField.put("type", "NUMBER");

            Map<String, Object> props = getOrCreateProps(frontendField);
            props.putIfAbsent("type", "number");
            return frontendField;
        }

        // Campo não reconhecido – retorna como está
        return frontendField;
    }

    private static boolean isMediaField(String name) {
        return "cover".equalsIgnoreCase(name)
                || "imagem".equalsIgnoreCase(name)
                || "foto".equalsIgnoreCase(name);
    }

    private static void applyMediaUpload(Map<String, Object> f, String entityType, String fieldName) {
        f.put("component", "QFile");
        f.put("immediateUpload", true);
        f.put("uploadEndpoint", "/api/" + entityType + "/{id}/upload-" + fieldName);
        f.put("mediaBasePath", "/media/" + entityType + "/");
        f.put("validationPreset", "image");
        f.put("showPreview", true);
        f.put("clearable", true);
        f.put("accept", "image/*");
        f.put("maxSize", 5 * 1024 * 1024);

        Map<String, Object> props = getOrCreateProps(f);
        props.putIfAbsent("filled", true);
        props.putIfAbsent("stack-label", true);
        props.putIfAbsent("use-chips", true);
        props.putIfAbsent("max-files", 1);
    }

    private static Map<String, Object> getOrCreateProps(Map<String, Object> field) {
        Map<String, Object> props;
        if (field.containsKey("props")) {
            props = (Map<String, Object>) field.get("props");
        } else {
            props = new HashMap<>();
            field.put("props", props);
        }
        return props;
    }
}