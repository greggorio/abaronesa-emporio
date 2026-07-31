package com.baronesa.emporio.dynamicform.entity;

public enum FormComplexityLevel {
    SIMPLE("Simples - 100% configurável via UI"),
    MEDIUM("Médio - Base UI + componentes customizados"),
    COMPLEX("Complexo - 100% código Java");

    private final String description;

    FormComplexityLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
