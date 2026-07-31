package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidadeProdutoLoteDTO {
    private Long estoqueLoteId;
    private String lote;
    private LocalDate dataValidade;
    private String status;
    private BigDecimal quantidade;
    private String rastreabilidade;
}
