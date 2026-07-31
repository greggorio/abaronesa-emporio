package com.baronesa.emporio.dto.relatorios;

public record DadosEmpresaDTO(
        String razaoSocial,
        String nomeFantasia,
        String cnpj,
        String inscricaoEstadual,
        String logradouro,
        String numero,
        String bairro,
        String municipio,
        String uf,
        String cep,
        String telefone,
        String logoPath
) {
    public String getEnderecoCompleto() {
        return String.format("%s, %s - %s - %s/%s - CEP: %s",
                logradouro, numero, bairro, municipio, uf, cep);
    }

    public String getCnpjFormatado() {
        if (cnpj == null || cnpj.length() != 14) return cnpj;
        return cnpj.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }
}
