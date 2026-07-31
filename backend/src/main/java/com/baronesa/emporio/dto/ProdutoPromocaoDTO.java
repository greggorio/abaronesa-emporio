package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.DiaSemana;
import com.baronesa.emporio.enums.TipoPromocao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoPromocaoDTO {
    private Long id;
    private Long produtoId;
    private String produtoNome;
    private DiaSemana diaSemana;
    private LocalTime horarioInicio;
    private LocalTime horarioFim;
    private TipoPromocao tipoPromocao;
    private BigDecimal percentualDesconto;
    private BigDecimal valorPromocional;
    private Boolean ativo;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
