package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaTecnicaDTO {
    private Long id;
    private Long produtoId;
    private String produtoNome;
    private BigDecimal custoTotal;
    private Integer rendimento;
    private String observacoes;
    @Builder.Default
    private List<FichaTecnicaItemDTO> itens = new ArrayList<>();
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
