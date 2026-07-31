package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.GamificacaoEventoTipo;
import com.baronesa.emporio.entity.Usuario;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class GamificacaoEvento {

    private GamificacaoEventoTipo tipo;
    private Long referenciaId;
    private Usuario cliente;
    private BigDecimal valor;
    private Integer quantidade;
    private Long pedidoId;
}
