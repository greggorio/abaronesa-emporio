package com.baronesa.emporio.dto;

import java.util.List;

public record CriarPedidoAdminRequest(
        Long sessaoConvidadoId,
        List<CriarPedidoRequest.Item> itens,
        String origem,
        Long compradorId
) {
}
