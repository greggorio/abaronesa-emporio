package com.baronesa.emporio.dto;

import java.time.LocalDateTime;

public record PagamentoHistoricoResponse(
        Long id,
        String beneficiario,     // Nome do beneficiário ou "Mesa toda"
        Long beneficiarioId,     // ID do beneficiário (null se mesa toda)
        String pagante,          // Nome de quem pagou ou null
        Long paganteId,          // ID de quem pagou ou null
        Long valorCentavos,
        Long valorBaseCentavos,
        Long valorCouvertCentavos,
        Long valorTaxaServicoCentavos,
        java.math.BigDecimal percentualTaxaServico,
        Boolean incluiTaxaServico,
        String metodo,
        String status,
        LocalDateTime criadoEm,
        LocalDateTime pagoEm
) {}
