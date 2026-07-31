package com.baronesa.emporio.dto.dashboard;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ProdutoPendenciaDTO(
        Long id,
        String nome,
        String codigoInterno,
        String sku,
        String categoriaNome,
        String subcategoriaNome,
        Double custo,
        LocalDateTime atualizadoEm
) {
}
