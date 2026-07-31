package com.baronesa.emporio.dto;

import java.util.List;

public record CriarPedidoRequest(
        List<Item> itens
) {
    public record Item(
            Long produtoId,
            Long skuId,
            Integer quantidade,
            String observacoes
    ) {}
}
