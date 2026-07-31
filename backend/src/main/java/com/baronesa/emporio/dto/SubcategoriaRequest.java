package com.baronesa.emporio.dto;

public record SubcategoriaRequest(
        String nome,
        Long categoriaId,
        String cover

) {}
