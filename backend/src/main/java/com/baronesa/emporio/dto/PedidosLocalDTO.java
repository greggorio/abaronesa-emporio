package com.baronesa.emporio.dto;

import lombok.Builder;
import java.util.List;

@Builder
public record PedidosLocalDTO(
        List<PedidosDiariosDTO> pedidosDiarios,
        Integer diasAnalisados
) {}
