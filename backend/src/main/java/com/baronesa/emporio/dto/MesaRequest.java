package com.baronesa.emporio.dto;

public record MesaRequest(
        String slug,
        String rotulo,
        String referencia,
        Boolean ativo
) {}
