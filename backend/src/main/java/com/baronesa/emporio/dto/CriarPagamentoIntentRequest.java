package com.baronesa.emporio.dto;

public record CriarPagamentoIntentRequest(
        String escopo, // convidado | mesa
        Long sessaoConvidadoId,
        Long sessaoMesaId,
        Long valorCentavos
) {}

