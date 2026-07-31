package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecebimentoHojeDTO {
    private LocalDate dataVencimento;
    private LocalDate dataRecebto;
    private String nomeCliente;
    private BigDecimal valor;
}