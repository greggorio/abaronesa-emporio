package com.baronesa.emporio.dto;

import java.util.List;

public record CriarPedidoResponse(
        Long pedidoId,
        String status,
        List<Item> itens
) {
    public record Item(
            Long itemPedidoId,
            Long produtoId,
            Integer quantidade,
            String status
    ) {}
}

