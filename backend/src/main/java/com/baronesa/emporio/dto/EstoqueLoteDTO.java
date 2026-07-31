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
public class EstoqueLoteDTO {
    private Long id;
    private String lote;
    private LocalDate dataValidade;
    private BigDecimal quantidade;
}
