package com.baronesa.emporio.nfe.dto;

import lombok.Data;

import java.util.List;

@Data
public class DanfeModel {
    private String chaveAcesso;
    private String numero;
    private String serie;
    private String dataEmissao;
    private String valorTotal;
    private Integer modelo; // 55 (NFe) ou 65 (NFCe)
    private String ambiente; // HOMOLOGACAO ou PRODUCAO

    private String valorPisTotal;
    private String valorCofinsTotal;
    private String valorTotTribTotal;
    private String valorTotalTributos;

    private String nomeEmitente;
    private String cnpjEmitente;
    private String enderecoEmitente;
    private String cidadeEmitente;
    private String bairroEmitente;
    private String cepEmitente;
    private String ieEmitente;
    private String telefoneEmitente;
    private String inscricaoMunicipal;

    private String referencia;
    private String vencimento;

    private String naturezaOperacao;
    private String ieSubstituto;

    private String nomeDestinatario;
    private String cpfDestinatario;
    private String cnpjDestinatario;
    private String enderecoDestinatario;
    private String cidadeDestinatario;
    private String nomeFantasiaDestinatario;
    private String bairroDestinatario;
    private String cepDestinatario;
    private String ufDestinatario;

    private String ieDestinatario;
    private String idCliente;
    private String telefoneDestinatario;

    private List<Produto> produtos;
    private List<Duplicata> duplicatas;

    private String baseCalculoIcms;
    private String valorIcms;
    private String baseCalculoIcmsSt;
    private String valorIcmsSt;
    private String valorTotalProdutos;
    private String valorFrete;
    private String valorSeguro;
    private String valorDesconto;
    private String outrasDespesas;
    private String valorIpi;
    private String valorTotalServicos;
    private String baseCalculoIssqn;
    private String valorIssqn;
    private String aliquotaIssqn;
    private String valorTotalNota;

    private String protocoloAutorizacao;
    private String dataAutorizacao;
    private String folha;
    private String qrBase64;
    private String dataEmissaoSimples;
    private String dataSaida;
    private String horaSaida;

    private String modalidadeFrete;
    private String transportadorNome;
    private String transportadorEndereco;
    private String transportadorMunicipio;
    private String transportadorUf;
    private String transportadorCnpj;
    private String transportadorCodigoAntt;
    private String veiculoPlaca;
    private String veiculoUf;
    private String volumesQuantidade;
    private String volumesEspecie;
    private String volumesMarca;
    private String volumesNumeracao;
    private String volumesPesoBruto;
    private String volumesPesoLiquido;

    private String informacoesComplementares;
    private String informacoesFisco;
    private String formaPagamento;

    public boolean isNFCe() {
        return modelo != null && modelo.equals(65);
    }

    @Data
    public static class Produto {
        private String codigo;
        private String descricao;
        private String quantidade;
        private String unidade;
        private String valorUnitario;
        private String valorTotal;
        private String valorDesconto;
        private String valorPis;
        private String valorCofins;
        private String valorTotTrib;
        private String ncm;
        private String cst;
        private String cfop;
        private String baseCalculoIcms;
        private String valorIcms;
        private String valorIpi;
        private String aliquotaIcms;
        private String aliquotaIpi;
        private String aliquotaPis;
        private String aliquotaCofins;
    }

    @Data
    public static class Duplicata {
        private String numero;
        private String vencimento;
        private String valor;
    }
}
