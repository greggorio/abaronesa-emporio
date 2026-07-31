package com.baronesa.emporio.nfe.service;

import com.baronesa.emporio.entity.NfeModel;
import com.baronesa.emporio.nfe.dto.DanfceModel;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço responsável por converter XML de NFC-e em DanfceModel
 * para geração do cupom fiscal eletrônico.
 *
 * Especializado em extrair apenas as informações essenciais
 * necessárias para impressão em formato 80mm.
 *
 * @author Sistema Loja (Ported to Bares)
 * @since 2025-01-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NfceParserService {

    private final ConfigManager configManager;

    // Formatadores
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("R$ #,##0.00");
    private static final DecimalFormat QUANTITY_FORMAT = new DecimalFormat("#,##0.000");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.00%");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Converte XML de NFC-e em modelo DANFCE otimizado para impressão
     */
    public DanfceModel parseNfceXml(NfeModel nfe) throws Exception {
        log.info("Iniciando parsing de NFC-e para DANFCE - ID: {}", nfe.getId());

        // Validações básicas
        validarDados(nfe);

        // Parse do XML
        Document document = criarDocumento(nfe.getXmlAssinado());
        XPath xpath = XPathFactory.newInstance().newXPath();
        
        // Configurar namespace para NFe
        xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                if ("nfe".equals(prefix)) {
                    return "http://www.portalfiscal.inf.br/nfe";
                }
                return javax.xml.XMLConstants.NULL_NS_URI;
            }
            public String getPrefix(String uri) {
                return null;
            }
            public java.util.Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });

        // Criar modelo base
        DanfceModel danfce = DanfceModel.builder()
                .chaveAcesso(nfe.getChaveAcesso())
                .numero(nfe.getNumero())
                .serie(nfe.getSerie())
                .homologacao(nfe.getAmbiente() != null && nfe.getAmbiente() == 2)
                .build();

        try {
            // Extrair dados do XML
            extrairIdentificacao(document, xpath, danfce, nfe);
            extrairEmitente(document, xpath, danfce);
            extrairDestinatario(document, xpath, danfce);
            extrairProdutos(document, xpath, danfce);
            extrairTotais(document, xpath, danfce);
            extrairPagamento(document, xpath, danfce);
            extrairQRCode(document, xpath, danfce);
            extrairInformacoesAdicionais(document, xpath, danfce);

            log.info("Parsing concluído com sucesso - {} produtos processados",
                    danfce.getProdutos().size());

            return danfce;

        } catch (Exception e) {
            log.error("Erro durante parsing do XML para DANFCE", e);
            throw new Exception("Erro ao processar dados da NFC-e: " + e.getMessage(), e);
        }
    }

    /**
     * Valida dados básicos da NFC-e
     */
    private void validarDados(NfeModel nfe) throws Exception {
        if (nfe == null) {
            throw new Exception("NfeModel não pode ser nulo");
        }

        if (nfe.getXmlAssinado() == null || nfe.getXmlAssinado().trim().isEmpty()) {
            throw new Exception("XML assinado da NFC-e não encontrado");
        }

        // 65 é o modelo da NFC-e
        if (nfe.getModelo() != null && nfe.getModelo() != 65) {
            log.warn("Modelo {} não é NFC-e, mas processando como DANFCE", nfe.getModelo());
        }
    }

    /**
     * Cria documento XML para parsing
     */
    private Document criarDocumento(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Extrai dados de identificação da NFC-e
     */
    private void extrairIdentificacao(Document doc, XPath xpath, DanfceModel danfce, NfeModel nfe) throws Exception {
        // Data de emissão
        String dhEmi = extrairTexto(xpath, "//nfe:dhEmi", doc);
        if (dhEmi != null && !dhEmi.isEmpty()) {
            try {
                // Formato ISO: 2025-01-31T10:30:45-03:00
                // Cortar para pegar apenas yyyy-MM-ddTHH:mm:ss
                if (dhEmi.length() >= 19) {
                   LocalDateTime dataEmissao = LocalDateTime.parse(dhEmi.substring(0, 19));
                   danfce.setDataEmissao(dataEmissao.format(DATE_TIME_FORMATTER));
                } else {
                   danfce.setDataEmissao(dhEmi);
                }
            } catch (Exception e) {
                log.warn("Erro ao parsear data de emissão: {}", dhEmi);
                danfce.setDataEmissao(dhEmi);
            }
        }

        // Protocolo de autorização
        if (nfe.getProtocolo() != null && !nfe.getProtocolo().isEmpty()) {
            danfce.setProtocoloAutorizacao(nfe.getProtocolo());

            // Data de autorização (usar data atual se não especificada)
            if (nfe.getDataEmissao() != null) {
                danfce.setDataAutorizacao(nfe.getDataEmissao().format(DATE_TIME_FORMATTER));
            }
        }

        // Mensagem de homologação
        if (danfce.isHomologacao()) {
            danfce.setMensagemHomologacao("EMITIDA EM AMBIENTE DE HOMOLOGAÇÃO - SEM VALOR FISCAL");
        }
    }

    /**
     * Extrai dados do emitente
     */
    private void extrairEmitente(Document doc, XPath xpath, DanfceModel danfce) throws Exception {
        danfce.setRazaoSocialEmitente(extrairTexto(xpath, "//nfe:emit/nfe:xNome", doc));
        danfce.setNomeFantasiaEmitente(extrairTexto(xpath, "//nfe:emit/nfe:xFant", doc));

        // CNPJ formatado
        String cnpj = extrairTexto(xpath, "//nfe:emit/nfe:CNPJ", doc);
        if (cnpj != null && cnpj.length() == 14) {
            danfce.setCnpjEmitente(formatarCnpj(cnpj));
        }

        danfce.setInscricaoEstadualEmitente(extrairTexto(xpath, "//nfe:emit/nfe:IE", doc));

        // Endereço completo
        String logradouro = extrairTexto(xpath, "//nfe:emit/nfe:enderEmit/nfe:xLgr", doc);
        String numero = extrairTexto(xpath, "//nfe:emit/nfe:enderEmit/nfe:nro", doc);
        String bairro = extrairTexto(xpath, "//nfe:emit/nfe:enderEmit/nfe:xBairro", doc);
        String cidade = extrairTexto(xpath, "//nfe:emit/nfe:enderEmit/nfe:xMun", doc);
        String uf = extrairTexto(xpath, "//nfe:emit/nfe:enderEmit/nfe:UF", doc);
        String cep = extrairTexto(xpath, "//nfe:emit/nfe:enderEmit/nfe:CEP", doc);

        StringBuilder endereco = new StringBuilder();
        if (logradouro != null) endereco.append(logradouro);
        if (numero != null) endereco.append(", ").append(numero);
        if (bairro != null) endereco.append(" - ").append(bairro);
        if (cidade != null && uf != null) endereco.append(" - ").append(cidade).append("/").append(uf);
        if (cep != null && cep.length() == 8) endereco.append(" - CEP: ").append(formatarCep(cep));

        danfce.setEnderecoEmitente(endereco.toString());

        // Telefone formatado
        String fone = extrairTexto(xpath, "//nfe:emit/nfe:enderEmit/nfe:fone", doc);
        if (fone != null && !fone.isEmpty()) {
            danfce.setTelefoneEmitente(formatarTelefone(fone));
        }
    }

    /**
     * Extrai dados do destinatário (consumidor)
     */
    private void extrairDestinatario(Document doc, XPath xpath, DanfceModel danfce) throws Exception {
        danfce.setNomeDestinatario(extrairTexto(xpath, "//nfe:dest/nfe:xNome", doc));

        // CPF ou CNPJ
        String cpf = extrairTexto(xpath, "//nfe:dest/nfe:CPF", doc);
        if (cpf != null && cpf.length() == 11) {
            danfce.setCpfDestinatario(formatarCpf(cpf));
        }

        String cnpj = extrairTexto(xpath, "//nfe:dest/nfe:CNPJ", doc);
        if (cnpj != null && cnpj.length() == 14) {
            danfce.setCnpjDestinatario(formatarCnpj(cnpj));
        }
    }

    /**
     * Extrai produtos/itens da NFC-e
     */
    private void extrairProdutos(Document doc, XPath xpath, DanfceModel danfce) throws Exception {
        NodeList produtos = (NodeList) xpath.evaluate("//nfe:det", doc, XPathConstants.NODESET);
        List<DanfceModel.ProdutoNfce> listaProdutos = new ArrayList<>();

        for (int i = 0; i < produtos.getLength(); i++) {
            Element det = (Element) produtos.item(i);

            DanfceModel.ProdutoNfce produto = DanfceModel.ProdutoNfce.builder()
                    .codigo(extrairTextoComNamespace(xpath, "nfe:prod/nfe:cProd", det))
                    .descricao(limitarTexto(extrairTextoComNamespace(xpath, "nfe:prod/nfe:xProd", det), 35))
                    .unidade(extrairTextoComNamespace(xpath, "nfe:prod/nfe:uCom", det))
                    .quantidade(formatarQuantidade(extrairTextoComNamespace(xpath, "nfe:prod/nfe:qCom", det)))
                    .valorUnitario(formatarMoeda(extrairTextoComNamespace(xpath, "nfe:prod/nfe:vUnCom", det)))
                    .valorTotal(formatarMoeda(extrairTextoComNamespace(xpath, "nfe:prod/nfe:vProd", det)))
                    .valorDesconto(formatarMoeda(extrairTextoComNamespace(xpath, "nfe:prod/nfe:vDesc", det)))
                    .build();

            listaProdutos.add(produto);
        }

        danfce.setProdutos(listaProdutos);
        danfce.setQuantidadeTotalItens(listaProdutos.size());
    }

    /**
     * Extrai totais da NFC-e
     */
    private void extrairTotais(Document doc, XPath xpath, DanfceModel danfce) throws Exception {
        // Valores principais
        String vProd = extrairTexto(xpath, "//nfe:ICMSTot/nfe:vProd", doc);
        String vOutro = extrairTexto(xpath, "//nfe:ICMSTot/nfe:vOutro", doc);
        danfce.setValorTotalProdutos(formatarMoeda(vProd));
        danfce.setValorDesconto(formatarMoeda(extrairTexto(xpath, "//nfe:ICMSTot/nfe:vDesc", doc)));
        danfce.setValorAcrescimo(formatarMoeda(vOutro));
        danfce.setValorTotalNota(formatarMoeda(extrairTexto(xpath, "//nfe:ICMSTot/nfe:vNF", doc)));

        // Tributos aproximados
        String vTotTrib = extrairTexto(xpath, "//nfe:ICMSTot/nfe:vTotTrib", doc);
        if (vTotTrib != null && !vTotTrib.isEmpty()) {
            danfce.setValorTributos(formatarMoeda(vTotTrib));

            // Calcular percentual aproximado
            try {
                BigDecimal tributos = new BigDecimal(vTotTrib);
                String vNF = extrairTexto(xpath, "//nfe:ICMSTot/nfe:vNF", doc);
                if (vNF != null && !vNF.isEmpty()) {
                    BigDecimal total = new BigDecimal(vNF);
                    if (total.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal percentual = tributos.divide(total, 4, BigDecimal.ROUND_HALF_UP);
                        danfce.setPercentualTributos(PERCENT_FORMAT.format(percentual));
                    }
                }
            } catch (Exception e) {
                log.warn("Erro ao calcular percentual de tributos", e);
            }
        }

        if (vOutro != null && !vOutro.isEmpty() && vProd != null && !vProd.isEmpty()) {
            try {
                BigDecimal acrescimo = new BigDecimal(vOutro);
                BigDecimal totalProdutos = new BigDecimal(vProd);
                if (acrescimo.compareTo(BigDecimal.ZERO) > 0 && totalProdutos.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal percentual = acrescimo.divide(totalProdutos, 4, BigDecimal.ROUND_HALF_UP);
                    danfce.setPercentualAcrescimo(PERCENT_FORMAT.format(percentual));
                }
            } catch (Exception e) {
                log.warn("Erro ao calcular percentual de acréscimo", e);
            }
        }
    }

    /**
     * Extrai informações de pagamento
     */
    private void extrairPagamento(Document doc, XPath xpath, DanfceModel danfce) throws Exception {
        NodeList detPagList = (NodeList) xpath.evaluate("//nfe:pag/nfe:detPag", doc, XPathConstants.NODESET);
        List<DanfceModel.PagamentoNfce> pagamentos = new ArrayList<>();

        if (detPagList != null) {
            for (int i = 0; i < detPagList.getLength(); i++) {
                if (!(detPagList.item(i) instanceof Element)) {
                    continue;
                }
                Element detPag = (Element) detPagList.item(i);
                String tPag = extrairTextoComNamespace(xpath, "nfe:tPag", detPag);
                String vPag = extrairTextoComNamespace(xpath, "nfe:vPag", detPag);
                DanfceModel.PagamentoNfce pagamento = DanfceModel.PagamentoNfce.builder()
                        .formaPagamento(converterFormaPagamento(tPag))
                        .valorPago(formatarMoeda(vPag))
                        .build();
                pagamentos.add(pagamento);
            }
        }

        if (!pagamentos.isEmpty()) {
            danfce.setPagamentos(pagamentos);
            danfce.setFormaPagamento(pagamentos.get(0).getFormaPagamento());
            danfce.setValorPago(pagamentos.get(0).getValorPago());
        } else {
            // Forma de pagamento principal (fallback)
            String tPag = extrairTexto(xpath, "//nfe:pag/nfe:detPag[1]/nfe:tPag", doc);
            danfce.setFormaPagamento(converterFormaPagamento(tPag));
            danfce.setValorPago(formatarMoeda(extrairTexto(xpath, "//nfe:pag/nfe:detPag[1]/nfe:vPag", doc)));
        }

        // Troco (se existir)
        String vTroco = extrairTexto(xpath, "//nfe:pag/nfe:vTroco", doc);
        if (vTroco != null && !vTroco.isEmpty()) {
            danfce.setValorTroco(formatarMoeda(vTroco));
        }
    }

    /**
     * Extrai QR Code da NFC-e
     */
    private void extrairQRCode(Document doc, XPath xpath, DanfceModel danfce) throws Exception {
        // QR Code do infNFeSupl
        String qrCode = extrairTexto(xpath, "//nfe:infNFeSupl/nfe:qrCode", doc);
        if (qrCode != null && !qrCode.isEmpty()) {
            danfce.setQrCode(qrCode);
        }

        // URL de consulta
        String urlConsulta = extrairTexto(xpath, "//nfe:infNFeSupl/nfe:urlChave", doc);
        if (urlConsulta != null && !urlConsulta.isEmpty()) {
            danfce.setUrlConsulta(urlConsulta);
        }

        // Se não tiver URL, usar padrão do estado
        if (danfce.getUrlConsulta() == null || danfce.getUrlConsulta().isEmpty()) {
            String uf = configManager.getConfig("nfe_uf", "SP");
            boolean homologacao = danfce.isHomologacao();
            danfce.setUrlConsulta(configManager.getConfig("nfce_url_consulta_" + uf.toLowerCase(),
                    homologacao ? "https://www.homologacao.nfce.fazenda.sp.gov.br/consulta"
                            : "https://www.nfce.fazenda.sp.gov.br/consulta"));
        }
    }

    /**
     * Extrai informações adicionais
     */
    private void extrairInformacoesAdicionais(Document doc, XPath xpath, DanfceModel danfce) throws Exception {
        String infCpl = extrairTexto(xpath, "//nfe:infAdic/nfe:infCpl", doc);
        danfce.setInformacoesComplementares(infCpl);

        // Observações fiscais padrão
        StringBuilder obs = new StringBuilder();

        if (danfce.getValorTributos() != null && !danfce.getValorTributos().equals("R$ 0,00")) {
            obs.append("Valor aproximado dos tributos: ").append(danfce.getValorTributos());
            if (danfce.getPercentualTributos() != null) {
                obs.append(" (").append(danfce.getPercentualTributos()).append(")");
            }
            obs.append(" - Fonte: IBPT");
        }

        danfce.setObservacoesFiscais(obs.toString());
    }

    // ========================================
    // MÉTODOS AUXILIARES
    // ========================================

    private String extrairTexto(XPath xpath, String expressao, Document doc) {
        try {
            return (String) xpath.evaluate(expressao, doc, XPathConstants.STRING);
        } catch (Exception e) {
            log.debug("Erro ao extrair texto com XPath {}: {}", expressao, e.getMessage());
            return null;
        }
    }

    private String extrairTextoComNamespace(XPath xpath, String expressao, Element element) {
        try {
            return (String) xpath.evaluate(expressao, element, XPathConstants.STRING);
        } catch (Exception e) {
            log.debug("Erro ao extrair texto com namespace {}: {}", expressao, e.getMessage());
            return null;
        }
    }

    private String formatarMoeda(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return "R$ 0,00";
        }

        try {
            BigDecimal decimal = new BigDecimal(valor);
            return CURRENCY_FORMAT.format(decimal);
        } catch (Exception e) {
            log.debug("Erro ao formatar moeda: {}", valor);
            return "R$ 0,00";
        }
    }

    private String formatarQuantidade(String quantidade) {
        if (quantidade == null || quantidade.trim().isEmpty()) {
            return "1,000";
        }

        try {
            BigDecimal decimal = new BigDecimal(quantidade);
            return QUANTITY_FORMAT.format(decimal);
        } catch (Exception e) {
            log.debug("Erro ao formatar quantidade: {}", quantidade);
            return quantidade;
        }
    }

    private String formatarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) return cpf;
        return cpf.substring(0, 3) + "." +
                cpf.substring(3, 6) + "." +
                cpf.substring(6, 9) + "-" +
                cpf.substring(9);
    }

    private String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) return cnpj;
        return cnpj.substring(0, 2) + "." +
                cnpj.substring(2, 5) + "." +
                cnpj.substring(5, 8) + "/" +
                cnpj.substring(8, 12) + "-" +
                cnpj.substring(12);
    }

    private String formatarCep(String cep) {
        if (cep == null || cep.length() != 8) return cep;
        return cep.substring(0, 5) + "-" + cep.substring(5);
    }

    private String formatarTelefone(String telefone) {
        if (telefone == null || telefone.length() < 10) return telefone;

        if (telefone.length() == 11) {
            return "(" + telefone.substring(0, 2) + ") " +
                    telefone.substring(2, 7) + "-" +
                    telefone.substring(7);
        } else if (telefone.length() == 10) {
            return "(" + telefone.substring(0, 2) + ") " +
                    telefone.substring(2, 6) + "-" +
                    telefone.substring(6);
        }

        return telefone;
    }

    private String converterFormaPagamento(String tPag) {
        if (tPag == null) return "Não informado";

        switch (tPag) {
            case "01": return "Dinheiro";
            case "02": return "Cheque";
            case "03": return "Cartão de Crédito";
            case "04": return "Cartão de Débito";
            case "05": return "Crédito Loja";
            case "10": return "Vale Alimentação";
            case "11": return "Vale Refeição";
            case "12": return "Vale Presente";
            case "13": return "Vale Combustível";
            case "15": return "Boleto Bancário";
            case "16": return "Depósito Bancário";
            case "17": return "PIX";
            case "18": return "Transferência Bancária";
            case "19": return "Programa de Fidelidade";
            case "90": return "Sem Pagamento";
            case "99": return "Outros";
            default: return "Forma " + tPag;
        }
    }

    private String limitarTexto(String texto, int limite) {
        if (texto == null) return null;
        if (texto.length() <= limite) return texto;
        return texto.substring(0, limite - 3) + "...";
    }
}
