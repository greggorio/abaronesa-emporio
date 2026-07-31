package com.baronesa.emporio.dto.delivery;

import com.baronesa.emporio.enums.TipoDeliveryPedido;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DeliveryOrderRequest {

    @NotNull
    private TipoDeliveryPedido tipo; // DELIVERY ou RETIRADA

    @NotEmpty
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerCpf;

    // Para DELIVERY
    private String dropoffAddress;
    private String dropoffNotes;

    private Integer deliveryFeeCents = 0;

    @NotEmpty
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long produtoId;
        private Long skuId;
        @NotNull
        private Integer quantidade;
        private String observacoes;
    }
}
