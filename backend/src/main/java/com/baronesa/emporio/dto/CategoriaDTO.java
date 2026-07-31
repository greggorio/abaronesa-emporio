package com.baronesa.emporio.dto;

import java.util.List;

public record CategoriaDTO(
        Long id,
        String nome,
        String icone,
        List<SubcategoriaDTO> subcategorias,
        String cover,
        Boolean exibirNoCardapio,
        Integer ordem
) {}