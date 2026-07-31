package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RecebimentoRequest(
        String numeroNf,
        Long fornecedorId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        LocalDateTime dataRecebimento,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dataEmissaoNf,
        String observacao,
        List<RecebimentoItemRequest> itens
) {}
