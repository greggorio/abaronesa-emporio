package com.baronesa.emporio.dto;

import java.time.LocalDate;

public record PedidosDiariosDTO(
        LocalDate data,
        Long pedidosBar,
        Long pedidosCozinha
) {}
