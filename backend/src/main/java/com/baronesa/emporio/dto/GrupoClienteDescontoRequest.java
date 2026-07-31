package com.baronesa.emporio.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record GrupoClienteDescontoRequest(
        @NotNull(message = "Categoria é obrigatória")
        Long categoriaId,

        Long subcategoriaId,

        @NotNull(message = "Percentual de desconto é obrigatório")
        @DecimalMin(value = "0.01", message = "Desconto deve ser maior que zero")
        @DecimalMax(value = "100.0", message = "Desconto não pode ser maior que 100%")
        BigDecimal descontoPercentual
) {}
