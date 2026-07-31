package com.baronesa.emporio.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StatusRecebimento {
    PENDENTE("Pendente", "warning", "o_hourglass_empty"),
    FINALIZADO("Finalizado", "positive", "o_check_circle"),
    CANCELADO("Cancelado", "negative", "o_cancel");

    private final String label;
    private final String color;
    private final String icon;

    public boolean isPendente() {
        return this == PENDENTE;
    }

    public boolean isFinalizado() {
        return this == FINALIZADO;
    }

    public boolean isCancelado() {
        return this == CANCELADO;
    }
}