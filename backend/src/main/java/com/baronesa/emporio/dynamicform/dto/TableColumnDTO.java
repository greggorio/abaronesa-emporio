package com.baronesa.emporio.dynamicform.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class TableColumnDTO {
    private String name;
    private String label;
    private String type;
    private String align = "left";
    private Integer width;
    private Boolean sortable = true;
    private Integer order;

    private Boolean visible;
    private String format;
    private String cellTemplate;
    private String classes;

    private String dataField;

    // Para colunas tipo LOOKUP
    private String lookupEndpoint;
    private List<String> displayColumns;
    private Boolean allowCreate;
    private String createDialogComponent;

    // Para colunas tipo COMPUTED
    private String formula;

    // Props genéricas
    private Map<String, Object> props = new HashMap<>();
}