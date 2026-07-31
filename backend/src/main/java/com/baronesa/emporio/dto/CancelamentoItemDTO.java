package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.MotivoCancelamentoItem;
import com.baronesa.emporio.enums.StatusItem;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class CancelamentoItemDTO {
    Long itemPedidoId;
    Long pedidoId;
    String produtoNome;
    Integer quantidade;
    BigDecimal precoUnitario;
    BigDecimal valorTotal;
    StatusItem status;
    String mesaSlug;
    String mesaRotulo;
    LocalDateTime criadoEm;
    MotivoCancelamentoItem motivoCodigo;
    String motivoDetalhe;
}
