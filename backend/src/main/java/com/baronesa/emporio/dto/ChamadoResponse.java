package com.baronesa.emporio.dto;

import java.time.LocalDateTime;

public record ChamadoResponse(
        Long id,
        Long sessaoMesaId,
        String mesaSlug,
        String mesaRotulo,
        String mesaReferencia,
        String tipo,
        String status,
        String observacao,
        LocalDateTime criadoEm,
        String atendidoPor,
        LocalDateTime atendidoEm,
        Long tempoEsperaSegundos  // Tempo desde criação (se pendente)
) {}
