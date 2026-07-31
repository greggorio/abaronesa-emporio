package com.baronesa.emporio.dto;

public record SubcategoriaDTO(
        Long id,
        String nome,
        String cover,
        Long categoriaId,
        String categoriaNome
) {}
