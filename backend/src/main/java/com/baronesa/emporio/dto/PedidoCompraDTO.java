package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.StatusPedidoCompra;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PedidoCompraDTO {
    private Long id;
    private Long fornecedorId;
    private String fornecedorNome;
    private StatusPedidoCompra status;
    private LocalDate dataPrevista;
    private String observacao;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private List<PedidoCompraItemDTO> itens;
    private BigDecimal valorTotal;
}
