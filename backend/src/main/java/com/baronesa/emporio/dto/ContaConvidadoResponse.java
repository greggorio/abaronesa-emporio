package com.baronesa.emporio.dto;

import java.util.List;

public record ContaConvidadoResponse(
        Long sessaoConvidadoId,
        String nome,
        Long grupoClienteId,
        String grupoClienteDescricao,
        long subtotalCentavos,
        long ajustesCentavos,
        long pagoCentavos,
        long devidoCentavos,
        List<Item> itens
) {
    public static record Item(
            Long pedidoId,
            String pedidoStatus,
            java.time.LocalDateTime pedidoCriadoEm,
            Long itemPedidoId,
            Long produtoId,
            String produtoNome,
            int quantidade,
            long precoUnitCentavos,
            String status,
            String observacoes
    ) {}
}
