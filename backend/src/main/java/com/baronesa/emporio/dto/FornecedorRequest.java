package com.baronesa.emporio.dto;

import jakarta.validation.constraints.NotBlank;

public record FornecedorRequest(
    @NotBlank(message = "Razão social é obrigatória")
    String razaoSocial,
    String nomeFantasia,
    String cnpj,
    String telefone,
    String email,
    String contato,
    String endereco,
    String cidade,
    String estado,
    String cep,
    Boolean ativo
) {}