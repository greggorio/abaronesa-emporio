package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.DiaSemana;
import lombok.*;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubcategoriaDisponibilidadeRequest {
    private Long subcategoriaId;
    private DiaSemana diaSemana;
    private LocalTime horarioInicio;
    private LocalTime horarioFim;
    private Boolean ativo;
}
