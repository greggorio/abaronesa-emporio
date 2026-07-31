package com.baronesa.emporio.enums;

public enum TaskStatus {
    BACKLOG("Backlog"),
    PLANNED("Planejada"),
    IN_PROGRESS("Em andamento"),
    PAUSED("Pausada"),
    BLOCKED("Bloqueada"),
    REVIEW("Em revisão"),
    COMPLETED("Concluída"),
    CANCELLED("Cancelada");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TaskStatus fromDisplayName(String displayName) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.getDisplayName().equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status não reconhecido: " + displayName);
    }
}