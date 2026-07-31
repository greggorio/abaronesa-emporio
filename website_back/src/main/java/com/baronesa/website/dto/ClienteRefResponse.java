package com.baronesa.website.dto;

public record ClienteRefResponse(
    Long id,
    String nome,
    String email,
    String telefone
) {
}