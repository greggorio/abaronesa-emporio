package com.baronesa.emporio.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceiroResumoDTO {
    private BigDecimal recebimentos;              // Total a receber hoje
    private BigDecimal total_recebido;            // Total já recebido hoje
    private BigDecimal pagamentos;                // Total a pagar hoje
    private BigDecimal total_pago;                // Total já pago hoje
    private BigDecimal pendencias_recebimento;    // Parcelas vencidas não recebidas
    private BigDecimal pendencias_pagamento;      // Parcelas vencidas não pagas
}
