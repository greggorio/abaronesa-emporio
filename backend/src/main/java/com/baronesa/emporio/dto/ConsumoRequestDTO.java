package com.baronesa.emporio.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

@Data
public class ConsumoRequestDTO {

    @NotNull(message = "Valor total é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true, message = "Valor total deve ser maior ou igual a zero")
    private Double valorTotal;

    @Size(max = 100, message = "Nome do cliente deve ter no máximo 100 caracteres")
    private String nomeCliente;
}