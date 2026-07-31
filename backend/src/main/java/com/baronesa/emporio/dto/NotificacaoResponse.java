package com.baronesa.emporio.dto;

import java.time.LocalDateTime;

public record NotificacaoResponse(
        Long id,
        String tipo,
        String titulo,
        String mensagem,
        boolean lida,
        LocalDateTime criadoEm,
        LocalDateTime lidaEm,
        String payloadJson
) {}
