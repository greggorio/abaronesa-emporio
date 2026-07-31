package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.OrigemCadastro;
import com.baronesa.emporio.enums.TipoPessoa;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClienteDTO(
        Long id,
        String nome,
        String email,
        String telefone,
        Boolean ativo,
        Boolean emailVerificado,
        Long grupoClienteId,
        String grupoClienteNome,
        
        // Dados de Identificação
        TipoPessoa tipoPessoa,
        String cpf,
        String cnpj,
        String inscricaoEstadual,
        LocalDate dataNascimento,
        
        // Endereço e Detalhes
        String endereco, // Mantido por compatibilidade
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado,
        String cep,
        String codigoMunicipioIbge,
        
        // Outros
        Boolean mensalista,
        OrigemCadastro origemCadastro,
        
        LocalDateTime criadoEm,
        LocalDateTime ultimoLogin
) {}