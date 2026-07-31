package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.OrigemCadastro;
import com.baronesa.emporio.enums.TipoPessoa;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "perfil_cliente")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true, nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa", length = 2)
    @Builder.Default
    private TipoPessoa tipoPessoa = TipoPessoa.PF;

    @Column(length = 11)
    private String cpf;

    @Column(length = 14)
    private String cnpj;

    @Column(name = "inscricao_estadual", length = 20)
    private String inscricaoEstadual;

    @Column(length = 255)
    private String endereco;

    @Column(length = 60)
    private String logradouro;

    @Column(length = 10)
    private String numero;

    @Column(length = 60)
    private String bairro;

    @Column(length = 60)
    private String complemento;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String estado;

    @Column(length = 9)
    private String cep;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "codigo_municipio_ibge", length = 7)
    private String codigoMunicipioIbge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_cliente_id")
    private GrupoCliente grupoCliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_cadastro", length = 20, nullable = false)
    @Builder.Default
    private OrigemCadastro origemCadastro = OrigemCadastro.LOJA_FISICA;

    @Column(name = "mensalista")
    @Builder.Default
    private Boolean mensalista = false;

    /**
     * Verifica se o perfil está completo para finalizar compras
     * @return true se todos os dados obrigatórios estão preenchidos
     */
    public boolean isPerfilCompleto() {
        // Validação básica para Pessoa Física
        if (tipoPessoa == TipoPessoa.PF) {
            return cpf != null && !cpf.trim().isEmpty() &&
                    logradouro != null && !logradouro.trim().isEmpty() &&
                    numero != null && !numero.trim().isEmpty() &&
                    bairro != null && !bairro.trim().isEmpty() &&
                    cidade != null && !cidade.trim().isEmpty() &&
                    estado != null && !estado.trim().isEmpty() &&
                    cep != null && !cep.trim().isEmpty();
        }

        // Validação para Pessoa Jurídica
        return cnpj != null && !cnpj.trim().isEmpty() &&
                logradouro != null && !logradouro.trim().isEmpty() &&
                numero != null && !numero.trim().isEmpty() &&
                bairro != null && !bairro.trim().isEmpty() &&
                cidade != null && !cidade.trim().isEmpty() &&
                estado != null && !estado.trim().isEmpty() &&
                cep != null && !cep.trim().isEmpty();
    }
}