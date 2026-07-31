package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesRecordDTO {
    private Long id;
    private LocalDateTime pagoEm;
    private String mesaSlug;
    private String mesaRotulo;
    private String beneficiario;
    private String pagante;
    private String metodo;
    private BigDecimal valor;
    private BigDecimal valorBase;
    private BigDecimal valorTaxaServico;
    private String providerRef;
}
