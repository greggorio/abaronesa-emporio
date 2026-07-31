package com.baronesa.emporio.dto;

public record CriarConvidadoResponse(
        Long sessaoConvidadoId,
        Long sessaoMesaId,
        String guestToken,
        Boolean host
) {}
