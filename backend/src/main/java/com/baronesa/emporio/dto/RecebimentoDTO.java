package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RecebimentoDTO(
        Long id,
        String numeroNf,
        String chaveNfe,
        Long fornecedorId,
        String fornecedorNome,
        String fornecedorCnpj,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime dataRecebimento,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dataEmissaoNf,
        BigDecimal valorTotal,
        Integer quantidadeItens,
        String status,
        String statusLabel,
        String statusColor,
        String observacao,
        List<RecebimentoItemDTO> itens,
        boolean podeEditar,
        boolean podeFinalizar,
        boolean podeCancelar
) {}