package com.baronesa.emporio.dto.formbuilder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO utilizado exclusivamente para a geração dinâmica do formulário de Funcionários.
 * Combina campos de Usuario e PerfilFuncionario em uma estrutura plana.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuncionarioFormFields {

    // Dados Principais (Usuario)
    private Long id;
    private String nome;
    private String email;
    private String telefone;
    private String senha;
    private Boolean ativo;

    // Permissões (Usuario)
    private Long grupoUsuarioId;
    private String roles; // Representação em string das roles para o formulário

    // Auditoria (Usuario)
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime ultimoLogin;

    // Dados Específicos de Funcionário (PerfilFuncionario)
    private BigDecimal voucherVr;
}