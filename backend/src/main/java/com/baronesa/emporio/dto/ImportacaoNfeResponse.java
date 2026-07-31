package com.baronesa.emporio.dto;

import java.util.List;

public record ImportacaoNfeResponse(
        boolean success,
        ImportacaoNfeDTO dados,
        List<String> avisos,
        List<String> erros
) {}