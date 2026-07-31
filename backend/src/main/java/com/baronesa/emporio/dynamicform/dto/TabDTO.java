package com.baronesa.emporio.dynamicform.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class TabDTO {
    private String name;
    private String label;
    private String icon;
    private String component; // Para tabs customizadas
    private String visibilityCondition; // Condição para exibir a aba (avaliada no frontend)
    private Map<String, Object> props; // Props personalizadas para componentes customizados
    private List<FieldDTO> fields = new ArrayList<>();
    private Integer order;
}
