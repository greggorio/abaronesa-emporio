package com.baronesa.emporio.dto;

import java.time.LocalDateTime;

public record ProdutoFrequenteDTO(
    Long produtoId,
    String nome,
    String imagem,
    Long quantidadeTotal,
    LocalDateTime ultimoPedidoEm
) {}
