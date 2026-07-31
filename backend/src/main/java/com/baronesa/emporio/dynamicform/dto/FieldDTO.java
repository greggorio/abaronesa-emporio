package com.baronesa.emporio.dynamicform.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class FieldDTO {
    private String name;
    private String label;
    private String type;
    private String component;
    private String placeholder;
    private boolean required;
    private String cols = "col-12";
    private Map<String, Object> props = new HashMap<>();
    private List<String> validations = new ArrayList<>();
    private String optionsEndpoint;
    private List<Map<String, Object>> options;
    private String formula; // Para campos computados
    private String visibilityCondition;
    private Boolean readOnly;

    // Campos adicionais para LOOKUP
    private String lookupEndpoint;
    private List<String> displayColumns;
    private Boolean allowCreate;
    private String createDialogComponent;

    // Campos adicionais para TABLE
    private String fieldType;
    private List<TableColumnDTO> columns;
    private Boolean rowAddable;
    private Boolean rowRemovable;

    // Campos adicionais gerais
    private String prefix; // Para campos CURRENCY/COMPUTED
    private String suffix; // Para campos COMPUTED
    private String accept; // Para campos FILE
    private Integer maxSize; // Para campos FILE
    private Boolean showPreview; // Para campos FILE
    private Boolean immediateUpload; // Para campos FILE
    private Boolean showOnEdit; // Para controlar visibilidade apenas em modo edição
}
