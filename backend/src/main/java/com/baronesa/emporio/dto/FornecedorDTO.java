package com.baronesa.emporio.dto;

public record FornecedorDTO(
    Long id,
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
    Boolean ativo,
    String nomeExibicao
) {}