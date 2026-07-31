package com.baronesa.emporio.dto;

import java.time.LocalDateTime;

public record MesaDTO(
        Long id,
        String slug,
        String rotulo,
        String referencia,
        Boolean ativo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}
