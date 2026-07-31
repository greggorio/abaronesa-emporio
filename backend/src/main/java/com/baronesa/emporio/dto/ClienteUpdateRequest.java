package com.baronesa.emporio.dto;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.OrigemCadastro;
import com.baronesa.emporio.enums.TipoPessoa;

import java.time.LocalDate;
import java.util.Set;

public record ClienteUpdateRequest(
        String nome,
        String email,
        String telefone,
        Boolean ativo,
        Long grupoClienteId,
        
        // Dados de identificação
        TipoPessoa tipoPessoa,
        String cpf,
        String cnpj,
        String inscricaoEstadual,
        LocalDate dataNascimento,
        
        // Endereço (campos existentes)
        String endereco,
        String cidade,
        String estado,
        String cep,
        
        // Endereço (campos detalhados)
        String logradouro,
        String numero,
        String bairro,
        String complemento,
        String codigoMunicipioIbge,
        
        // Metadados
        OrigemCadastro origemCadastro,
        Boolean mensalista,
        
        Set<Usuario.Role> roles
) {}