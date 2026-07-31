package com.baronesa.emporio.dto.formbuilder;

import com.baronesa.emporio.enums.OrigemCadastro;
import com.baronesa.emporio.enums.TipoPessoa;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO utilizado exclusivamente para a geração dinâmica do formulário de Clientes.
 * Combina campos de Usuario e PerfilCliente em uma estrutura plana.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteFormFields {

    // Dados Principais (Usuario)
    private Long id;
    private String nome;
    private String email;
    private String telefone;
    private Boolean ativo;

    // Classificação
    private Long grupoClienteId;
    private Boolean mensalista;
    private OrigemCadastro origemCadastro;

    // Dados de Pessoa (PerfilCliente)
    private TipoPessoa tipoPessoa;
    private String cpf;
    private String cnpj;
    private String inscricaoEstadual;
    private LocalDate dataNascimento;

    // Endereço (PerfilCliente)
    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String estado;
    private String codigoMunicipioIbge;
}
