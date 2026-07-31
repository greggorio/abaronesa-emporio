package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.TipoPessoa;

/**
 * DTO específico para dados do cliente no processo de checkout
 * Contém todos os campos necessários para finalização de compra
 */
public record ClienteCheckoutDTO(
        // Dados básicos do usuário
        Long id,
        String nome,
        String email,
        String telefone,

        // Dados fiscais
        TipoPessoa tipoPessoa,
        String cpf,
        String cnpj,
        String inscricaoEstadual,

        // Endereço completo
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado,
        String cep,
        String codigoMunicipioIbge,

        // Status do perfil
        boolean perfilCompleto,
        boolean emailVerificado
) {
    /**
     * Construtor para Pessoa Física
     */
    public static ClienteCheckoutDTO criarPessoaFisica(
            Long id,
            String nome,
            String email,
            String telefone,
            String cpf,
            String logradouro,
            String numero,
            String complemento,
            String bairro,
            String cidade,
            String estado,
            String cep,
            String codigoMunicipioIbge,
            boolean perfilCompleto,
            boolean emailVerificado
    ) {
        return new ClienteCheckoutDTO(
                id, nome, email, telefone,
                TipoPessoa.PF, cpf, null, null,
                logradouro, numero, complemento, bairro,
                cidade, estado, cep, codigoMunicipioIbge,
                perfilCompleto, emailVerificado
        );
    }

    /**
     * Construtor para Pessoa Jurídica
     */
    public static ClienteCheckoutDTO criarPessoaJuridica(
            Long id,
            String nome,
            String email,
            String telefone,
            String cnpj,
            String inscricaoEstadual,
            String logradouro,
            String numero,
            String complemento,
            String bairro,
            String cidade,
            String estado,
            String cep,
            String codigoMunicipioIbge,
            boolean perfilCompleto,
            boolean emailVerificado
    ) {
        return new ClienteCheckoutDTO(
                id, nome, email, telefone,
                TipoPessoa.PJ, null, cnpj, inscricaoEstadual,
                logradouro, numero, complemento, bairro,
                cidade, estado, cep, codigoMunicipioIbge,
                perfilCompleto, emailVerificado
        );
    }
}