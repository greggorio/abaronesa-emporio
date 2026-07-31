package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.StatusPedidoCompra;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AtualizarPedidoCompraRequest {
    private Long fornecedorId;
    private LocalDate dataPrevista;
    private String observacao;
    private StatusPedidoCompra status;
}
