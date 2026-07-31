package com.baronesa.emporio.dto;

public record SKUOptionDTO(
        Long value,
        String label,
        String codigoBarras,
        Double preco,
        Integer estoque
) {
    // Construtor simplificado para compatibilidade
    public SKUOptionDTO(Long value, String label) {
        this(value, label, null, null, null);
    }
}
