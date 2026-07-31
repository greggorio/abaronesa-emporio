package com.baronesa.emporio.dto.relatorios;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumoVendasProdutoDTO {
    private BigDecimal faturamentoTotal;
    private Long totalItensVendidos;
    private Long quantidadeProdutosDistintos;
    private BigDecimal ticketMedio;
}
