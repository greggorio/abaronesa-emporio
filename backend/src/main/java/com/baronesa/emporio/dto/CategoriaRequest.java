package com.baronesa.emporio.dto;

public record CategoriaRequest(
        String nome,
        String icone,
        String cover,
        Boolean exibirNoCardapio,
        Integer ordem
) {}