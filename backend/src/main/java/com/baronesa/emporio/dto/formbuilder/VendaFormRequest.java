package com.baronesa.emporio.dto.formbuilder;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VendaFormRequest {
    // Campos básicos de exibição
    private Long id;
    private LocalDateTime criadoEm;
    private LocalDateTime pagoEm;
    private String status;
    private String metodo;

    // Mesa / Sessão
    private String mesaSlug;
    private String mesaRotulo;

    // Cliente / Pagante
    private String beneficiario;
    private Long beneficiarioId;
    private String pagante;
    private Long paganteId;

    // Valores / Referência
    private BigDecimal valor;
    private String providerRef;
}

