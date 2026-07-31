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
public class ProdutoVendaDTO {
    private Long produtoId;
    private String nome;
    private Long quantidade;
    private BigDecimal valorUnitarioMedio;
    private BigDecimal valorTotal;
    private BigDecimal percentualParticipacao;
}
