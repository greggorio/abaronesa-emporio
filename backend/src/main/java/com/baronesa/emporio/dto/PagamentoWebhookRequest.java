package com.baronesa.emporio.dto;

public record PagamentoWebhookRequest(
        String provedor,
        String evento, // payment.paid
        String referenciaProvedor,
        Long pagamentoId,
        Long valorCentavos
) {}

