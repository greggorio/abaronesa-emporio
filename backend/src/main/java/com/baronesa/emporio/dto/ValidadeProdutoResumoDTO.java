package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidadeProdutoResumoDTO {
    private Integer totalSkus;
    private Integer totalLotes;
    private Integer lotesComSaldo;
    private Integer vencidos;
    private Integer criticos;
    private Integer atencao;
    private Integer semLote;
}
