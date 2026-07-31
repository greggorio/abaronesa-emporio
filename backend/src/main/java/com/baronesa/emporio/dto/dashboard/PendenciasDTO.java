package com.baronesa.emporio.dto.dashboard;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PendenciasDTO(
        Long total,
        Long produtosSemPreco,
        Long produtosSemEstoque,
        LocalDateTime geradoEm
) {
}
