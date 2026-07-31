package com.baronesa.emporio.dto;

public record CriarChamadoRequest(
        Long sessaoMesaId,
        String tipo,           // garcom | conta | ajuda
        String observacao      // Opcional
) {}
