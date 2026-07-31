package com.baronesa.emporio.dynamicform.dto;

import com.baronesa.emporio.dynamicform.entity.FormComplexityLevel;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class FormDefinitionDTO {
    private String entityType;
    private String programName;
    private String programIcon;
    private String tableOrder;
    private FormComplexityLevel complexity;
    private List<TabDTO> tabs = new ArrayList<>();
    private List<ActionDTO> actions = new ArrayList<>();
    private Map<String, Object> customSlots = new HashMap<>();
    private List<TableColumnDTO> tableColumns = new ArrayList<>();
    private String javaExtensionClass;
    private Map<String, Object> dialogConfig = new HashMap<>();
}