package com.baronesa.emporio.dynamicform.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ActionDTO {
    private String type; // ADD, EDIT, DELETE, CUSTOM
    private String name;
    private String label;
    private String icon;
    private String color;
    private String endpoint;
    private String route;
    private String method = "POST";
    private Boolean opensDialog;
    private String dialogComponent;
    private Boolean requiresSelection;
    private Boolean inlineOnly;
    private Boolean onDoubleClick;
    private String successMessage;
    private String confirmTitle;
    private String confirmMessage;
    private String condition;
    private Boolean reloadAfterSuccess;
    private Map<String, Object> props = new HashMap<>();
}