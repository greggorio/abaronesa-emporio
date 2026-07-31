package com.baronesa.emporio.dto.dashboard;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record VendasPeriodoDTO(
        BigDecimal faturamento,
        Long quantidadeVendas,
        BigDecimal ticketMedio,
        BigDecimal pix,
        BigDecimal cartao,
        BigDecimal cartaoCredito,
        BigDecimal cartaoDebito,
        BigDecimal voucher,
        BigDecimal dinheiro,
        BigDecimal faturamentoBase,
        BigDecimal taxaServico
) {}
