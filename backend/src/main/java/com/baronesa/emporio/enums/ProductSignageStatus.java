package com.baronesa.emporio.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductSignageStatus {
    PENDING("pending"),
    AI_GENERATED("ai_generated"),
    RENDERED("rendered"),
    LINKED("linked"),
    ERROR("error");

    private final String value;

    ProductSignageStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ProductSignageStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ProductSignageStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ProductSignageStatus: " + value);
    }
}
