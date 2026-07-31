package com.baronesa.emporio.nfe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo de dados específico para DANFCE (Documento Auxiliar da NFC-e).
 *
 * Estrutura otimizada para impressão térmica 80mm com informações essenciais
 * para o cupom fiscal eletrônico.
 *
 * @author Sistema Loja (Ported to Bares)
 * @since 2025-01-31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DanfceModel {

    // ========================================
    // IDENTIFICAÇÃO DA NOTA
    // ========================================

    /**
     * Número da NFC-e
     */
    private String numero;

    /**
     * Série da NFC-e
     */
    private String serie;

    /**
     * Chave de acesso de 44 dígitos
     */
    private String chaveAcesso;

    /**
     * Data e hora de emissão formatada (dd/MM/yyyy HH:mm:ss)
     */
    private String dataEmissao;

    /**
     * Protocolo de autorização da SEFAZ
     */
    private String protocoloAutorizacao;

    /**
     * Data de autorização formatada
     */
    private String dataAutorizacao;

    // ========================================
    // DADOS DO EMITENTE
    // ========================================

    /**
     * Razão social do emitente
     */
    private String razaoSocialEmitente;

    /**
     * Nome fantasia do emitente
     */
    private String nomeFantasiaEmitente;

    /**
     * CNPJ do emitente formatado (XX.XXX.XXX/XXXX-XX)
     */
    private String cnpjEmitente;

    /**
     * Inscrição estadual do emitente
     */
    private String inscricaoEstadualEmitente;

    /**
     * Endereço completo do emitente
     */
    private String enderecoEmitente;

    /**
     * Telefone do emitente formatado
     */
    private String telefoneEmitente;

    // ========================================
    // DADOS DO DESTINATÁRIO (OPCIONAL PARA NFCE)
    // ========================================

    /**
     * Nome do destinatário (consumidor)
     */
    private String nomeDestinatario;

    /**
     * CPF do destinatário formatado (XXX.XXX.XXX-XX)
     */
    private String cpfDestinatario;

    /**
     * CNPJ do destinatário formatado (XX.XXX.XXX/XXXX-XX)
     */
    private String cnpjDestinatario;

    // ========================================
    // PRODUTOS/ITENS
    // ========================================

    /**
     * Lista de produtos da NFC-e
     */
    @Builder.Default
    private List<ProdutoNfce> produtos = new ArrayList<>();

    // ========================================
    // TOTAIS E VALORES
    // ========================================

    /**
     * Quantidade total de itens
     */
    private Integer quantidadeTotalItens;

    /**
     * Valor total dos produtos formatado (R$ X.XXX,XX)
     */
    private String valorTotalProdutos;

    /**
     * Valor de desconto formatado (R$ X.XXX,XX)
     */
    private String valorDesconto;

    /**
     * Valor de acréscimo formatado (R$ X.XXX,XX)
     */
    private String valorAcrescimo;

    /**
     * Percentual do acréscimo (ex.: 10,00%)
     */
    private String percentualAcrescimo;

    /**
     * Valor total da nota formatado (R$ X.XXX,XX)
     */
    private String valorTotalNota;

    // ========================================
    // TRIBUTOS (PARA EXIBIÇÃO NO DANFCE)
    // ========================================

    /**
     * Valor aproximado dos tributos formatado (R$ X.XXX,XX)
     */
    private String valorTributos;

    /**
     * Percentual aproximado dos tributos (XX,XX%)
     */
    private String percentualTributos;

    // ========================================
    // PAGAMENTO
    // ========================================

    /**
     * Lista de formas de pagamento (quando houver mais de uma)
     */
    @Builder.Default
    private List<PagamentoNfce> pagamentos = new ArrayList<>();

    /**
     * Forma de pagamento principal
     */
    private String formaPagamento;

    /**
     * Valor pago formatado (R$ X.XXX,XX)
     */
    private String valorPago;

    /**
     * Valor do troco formatado (R$ X.XXX,XX)
     */
    private String valorTroco;

    // ========================================
    // QR CODE E CONSULTA
    // ========================================

    /**
     * Conteúdo do QR Code para consulta
     */
    private String qrCode;

    /**
     * URL para consulta da NFC-e
     */
    private String urlConsulta;

    // ========================================
    // INFORMAÇÕES ADICIONAIS
    // ========================================

    /**
     * Informações complementares para exibição no DANFCE
     */
    private String informacoesComplementares;

    /**
     * Observações fiscais obrigatórias
     */
    private String observacoesFiscais;

    // ========================================
    // DADOS PARA TEMPLATE
    // ========================================

    /**
     * Indica se está em ambiente de homologação
     */
    private Boolean homologacao;

    /**
     * Mensagem de homologação para exibir no cupom
     */
    private String mensagemHomologacao;

    // ========================================
    // CLASSE INTERNA: PRODUTO NFCE
    // ========================================

    /**
     * Representa um produto/item da NFC-e para exibição no DANFCE
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProdutoNfce {

        /**
         * Código do produto
         */
        private String codigo;

        /**
         * Descrição do produto (limitada para cupom)
         */
        private String descricao;

        /**
         * Unidade de medida
         */
        private String unidade;

        /**
         * Quantidade formatada (X,XXX)
         */
        private String quantidade;

        /**
         * Valor unitário formatado (R$ X.XXX,XX)
         */
        private String valorUnitario;

        /**
         * Valor total do item formatado (R$ X.XXX,XX)
         */
        private String valorTotal;

        /**
         * Valor de desconto do item formatado (R$ X.XXX,XX)
         */
        private String valorDesconto;
    }

    /**
     * Representa uma forma de pagamento para exibição no DANFCE
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PagamentoNfce {
        private String formaPagamento;
        private String valorPago;
    }

    // ========================================
    // MÉTODOS AUXILIARES
    // ========================================

    /**
     * Adiciona um produto à lista
     */
    public void adicionarProduto(ProdutoNfce produto) {
        if (this.produtos == null) {
            this.produtos = new ArrayList<>();
        }
        this.produtos.add(produto);
    }

    /**
     * Adiciona um pagamento à lista
     */
    public void adicionarPagamento(PagamentoNfce pagamento) {
        if (this.pagamentos == null) {
            this.pagamentos = new ArrayList<>();
        }
        this.pagamentos.add(pagamento);
    }

    /**
     * Verifica se tem produtos
     */
    public boolean temProdutos() {
        return produtos != null && !produtos.isEmpty();
    }

    /**
     * Verifica se tem pagamentos informados
     */
    public boolean temPagamentos() {
        return pagamentos != null && !pagamentos.isEmpty();
    }

    /**
     * Verifica se tem destinatário identificado
     */
    public boolean temDestinatario() {
        return (cpfDestinatario != null && !cpfDestinatario.trim().isEmpty()) ||
                (cnpjDestinatario != null && !cnpjDestinatario.trim().isEmpty());
    }

    /**
     * Verifica se está em homologação
     */
    public boolean isHomologacao() {
        return homologacao != null && homologacao;
    }

    /**
     * Obtém a chave de acesso formatada para exibição (grupos de 4 dígitos)
     */
    public String getChaveAcessoFormatada() {
        if (chaveAcesso == null || chaveAcesso.length() != 44) {
            return chaveAcesso;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chaveAcesso.length(); i += 4) {
            if (i > 0) sb.append(" ");
            sb.append(chaveAcesso.substring(i, Math.min(i + 4, chaveAcesso.length())));
        }
        return sb.toString();
    }

    /**
     * Obtém mensagem de rodapé padrão para DANFCE
     */
    public String getMensagemRodape() {
        if (isHomologacao()) {
            return "EMITIDA EM AMBIENTE DE HOMOLOGAÇÃO - SEM VALOR FISCAL";
        }
        return "Consulte pela Chave de Acesso em: " + (urlConsulta != null ? urlConsulta : "portal.fazenda.gov.br");
    }
}
