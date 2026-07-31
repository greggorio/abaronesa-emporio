package com.baronesa.emporio.dto;

public record ClientesResumoResponse(
    long totalClientes,
    long novosPeriodo,
    int periodoDias
) {}
