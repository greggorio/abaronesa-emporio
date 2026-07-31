package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.DiaSemana;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoDisponibilidadeDTO {
    private Long id;
    private Long produtoId;
    private String produtoNome;
    private DiaSemana diaSemana;
    private LocalTime horarioInicio;
    private LocalTime horarioFim;
    private Boolean ativo;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
