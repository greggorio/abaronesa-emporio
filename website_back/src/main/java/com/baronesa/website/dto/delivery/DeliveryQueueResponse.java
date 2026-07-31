package com.baronesa.website.dto.delivery;

import java.time.Instant;
import java.util.List;

public record DeliveryQueueResponse(
        List<KdsTicketDto> tickets
) {
    public record KdsTicketDto(
            Long itemPedidoId,
            Long pedidoId,
            String estacao,
            String status,
            Instant atualizadoEm,
            String tipo,
            Long deliveryItemId,
            DeliveryInfo delivery,
            ItemInfo item,
            MesaInfo mesa,
            PedidoInfo pedido
    ) {}

    public record DeliveryInfo(
            String deliveryId,
            String externalId,
            String customerName,
            String dropoffAddress,
            String status
    ) {}

    public record ItemInfo(
            String nome,
            Integer quantidade,
            String observacoes,
            Boolean necessitaPreparacao,
            Long skuId,
            String variacao
    ) {}

    public record MesaInfo(
            String slug,
            String rotulo,
            String referencia,
            String serviceMode
    ) {}

    public record PedidoInfo(
            Instant criadoEm
    ) {}
}
