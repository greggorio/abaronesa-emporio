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
public class ValidadeProdutoLoteMovimentoDTO {
    private Long id;
    private LocalDateTime dataMovimento;
    private Integer tipoMovimento;
    private String tipoMovimentoDescricao;
    private BigDecimal deltaQuantidade;
    private String observacao;
    private String documentoReferencia;
    private String usuarioNome;
}
