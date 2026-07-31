package com.baronesa.emporio.dto;

public record PagamentoIntentResponse(
        Long pagamentoId,
        String metodo,
        String status,
        String qrPayload
) {}

