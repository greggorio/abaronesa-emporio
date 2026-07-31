package com.baronesa.emporio.dto;

import java.math.BigDecimal;

public record GrupoClienteDescontoDTO(
        Long id,
        Long grupoClienteId,
        Long categoriaId,
        Long subcategoriaId,
        BigDecimal descontoPercentual,
        Boolean ativo
) {}
