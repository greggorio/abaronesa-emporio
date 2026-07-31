package com.baronesa.emporio.dto;

public record ClienteOptionDTO(
        Long value,
        String label,
        String telefone,
        String email
) {}