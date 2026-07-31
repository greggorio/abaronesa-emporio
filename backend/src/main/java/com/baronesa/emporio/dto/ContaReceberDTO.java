package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ContaReceberDTO(
        Long id,
        Long clienteId,
        String clienteNome,
        Long tipoReceitaId,
        String tipoReceitaNome,
        String numeroDocumento,
        String descricao,
        BigDecimal valorTotal,
        Integer numeroParcelas,
        String observacoes,
        boolean recorrente,
        LocalDateTime dataCadastro,
        boolean quitada,
        BigDecimal valorRecebido,
        BigDecimal valorPendente,
        List<ContaReceberParcelaDTO> parcelas
) {}