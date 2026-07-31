package com.baronesa.emporio.dto;

public record FornecedorNfeDTO(
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        Long fornecedorId, // Se já existe no sistema
        boolean cadastrado
) {}