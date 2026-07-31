package com.baronesa.emporio.dto.dashboard;

import java.math.BigDecimal;

public record ConsumoVoucherDTO(
        Long usuarioId,
        String nome,
        BigDecimal totalConsumido,
        BigDecimal voucherVr,
        BigDecimal excedente
) {}
