package com.baronesa.emporio.dto;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.OrigemCadastro;
import com.baronesa.emporio.enums.TipoPessoa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record ClienteRequest(
        Long id,
        @NotBlank(message = "Nome é obrigatório")
        String nome,
        
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ter formato válido")
        String email,
        
        String telefone,
        
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        String senha,

        Set<Usuario.Role> roles,
        
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
        OrigemCadastro origemCadastro
) {}