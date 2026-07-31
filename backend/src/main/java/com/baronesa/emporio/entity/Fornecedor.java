package com.baronesa.emporio.entity;

import com.baronesa.emporio.service.lookup.LookupSearchable;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "fornecedor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fornecedor implements LookupSearchable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String razaoSocial;

    private String nomeFantasia;

    @Column(unique = true)
    private String cnpj;

    private String telefone;

    private String email;

    private String contato; // Nome da pessoa de contato

    private String endereco;

    private String cidade;

    private String estado;

    private String cep;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    // Método utilitário para exibição
    public String getNomeExibicao() {
        if (nomeFantasia != null && !nomeFantasia.trim().isEmpty()) {
            return nomeFantasia;
        }
        return razaoSocial;
    }

    // ===== Implementação da interface LookupSearchable =====

    @Override
    public String getLookupLabel() {
        StringBuilder label = new StringBuilder();

        // Se tem CNPJ, incluir no label
        if (cnpj != null && !cnpj.trim().isEmpty()) {
            label.append(cnpj).append(" - ");
        }

        // Usar nome fantasia se disponível, senão razão social
        String nome = (nomeFantasia != null && !nomeFantasia.trim().isEmpty())
                ? nomeFantasia
                : razaoSocial;

        label.append(nome);

        return label.toString();
    }

    @Override
    public Map<String, Object> getLookupData() {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("cnpj", cnpj);
        data.put("razaoSocial", razaoSocial);
        data.put("nomeFantasia", nomeFantasia);
        data.put("cidade", cidade);

        // Adicionar informações extras úteis
        if (telefone != null && !telefone.trim().isEmpty()) {
            data.put("telefone", telefone);
        }

        if (email != null && !email.trim().isEmpty()) {
            data.put("email", email);
        }

        // Indicador se está ativo
        data.put("ativo", ativo != null ? ativo : true);

        return data;
    }
}