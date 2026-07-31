package com.baronesa.emporio.dto;

public record RegistrarPagamentoAlocacaoRequest(
        Long sessaoConvidadoId,
        Long valorCentavos
) {}

