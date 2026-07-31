package com.baronesa.emporio.nfe.service;

import com.baronesa.emporio.entity.NfeModel;
import com.baronesa.emporio.nfe.dto.DanfeModel;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser de XML da NFe (modelo 55) para {@link DanfeModel},
 * utilizado na geração do DANFE (A4).
 */
@Slf4j
@Service
public class NfeParserService {

    private static final DateTimeFormatter OUT_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public DanfeModel parseNfeXml(NfeModel nfe) throws Exception {
        if (nfe == null || nfe.getXmlAssinado() == null || nfe.getXmlAssinado().isBlank()) {
            throw new IllegalArgumentException("NFe sem XML assinado para parse");
        }

        Document doc = buildDocument(nfe.getXmlAssinado());
        XPath xpath = buildXPath();

        DanfeModel danfe = new DanfeModel();
        danfe.setModelo(55);
        danfe.setChaveAcesso(nfe.getChaveAcesso());
        danfe.setNumero(nfe.getNumero());
        danfe.setSerie(nfe.getSerie());
        danfe.setFolha("1/1");

        // Identificação / datas
        String dhEmi = text(xpath, "//nfe:ide/nfe:dhEmi", doc);
        danfe.setDataEmissao(formatDhEmi(dhEmi));
        danfe.setAmbiente(mapAmbiente(text(xpath, "//nfe:ide/nfe:tpAmb", doc)));
        danfe.setNaturezaOperacao(text(xpath, "//nfe:ide/nfe:natOp", doc));
        danfe.setDataEmissaoSimples(formatDateOnly(dhEmi));

        // Totais
        danfe.setValorTotalNota(text(xpath, "//nfe:ICMSTot/nfe:vNF", doc));
        danfe.setValorTotalProdutos(text(xpath, "//nfe:ICMSTot/nfe:vProd", doc));
        danfe.setValorDesconto(text(xpath, "//nfe:ICMSTot/nfe:vDesc", doc));
        danfe.setBaseCalculoIcms(text(xpath, "//nfe:ICMSTot/nfe:vBC", doc));
        danfe.setValorIcms(text(xpath, "//nfe:ICMSTot/nfe:vICMS", doc));
        danfe.setBaseCalculoIcmsSt(text(xpath, "//nfe:ICMSTot/nfe:vBCST", doc));
        danfe.setValorIcmsSt(text(xpath, "//nfe:ICMSTot/nfe:vST", doc));
        danfe.setValorPisTotal(text(xpath, "//nfe:ICMSTot/nfe:vPIS", doc));
        danfe.setValorCofinsTotal(text(xpath, "//nfe:ICMSTot/nfe:vCOFINS", doc));
        danfe.setValorTotTribTotal(text(xpath, "//nfe:ICMSTot/nfe:vTotTrib", doc));
        danfe.setValorTotalTributos(text(xpath, "//nfe:ICMSTot/nfe:vTotTrib", doc));
        danfe.setValorFrete(text(xpath, "//nfe:ICMSTot/nfe:vFrete", doc));
        danfe.setValorSeguro(text(xpath, "//nfe:ICMSTot/nfe:vSeg", doc));
        danfe.setOutrasDespesas(text(xpath, "//nfe:ICMSTot/nfe:vOutro", doc));
        danfe.setValorIpi(text(xpath, "//nfe:ICMSTot/nfe:vIPI", doc));

        // Emitente
        danfe.setNomeEmitente(text(xpath, "//nfe:emit/nfe:xNome", doc));
        danfe.setCnpjEmitente(formatCnpj(text(xpath, "//nfe:emit/nfe:CNPJ", doc)));
        danfe.setIeEmitente(text(xpath, "//nfe:emit/nfe:IE", doc));
        danfe.setInscricaoMunicipal(text(xpath, "//nfe:emit/nfe:IM", doc));
        danfe.setEnderecoEmitente(montaEndereco(xpath, doc, "//nfe:emit/nfe:enderEmit"));
        danfe.setCidadeEmitente(montaCidadeUf(xpath, doc, "//nfe:emit/nfe:enderEmit"));
        danfe.setBairroEmitente(text(xpath, "//nfe:emit/nfe:enderEmit/nfe:xBairro", doc));
        danfe.setCepEmitente(formatCep(text(xpath, "//nfe:emit/nfe:enderEmit/nfe:CEP", doc)));
        danfe.setTelefoneEmitente(text(xpath, "//nfe:emit/nfe:enderEmit/nfe:fone", doc));

        // Destinatário
        danfe.setNomeDestinatario(text(xpath, "//nfe:dest/nfe:xNome", doc));
        String cpfDest = text(xpath, "//nfe:dest/nfe:CPF", doc);
        String cnpjDest = text(xpath, "//nfe:dest/nfe:CNPJ", doc);
        danfe.setCpfDestinatario(formatCpf(cpfDest));
        danfe.setCnpjDestinatario(formatCnpj(cnpjDest));
        danfe.setIeDestinatario(text(xpath, "//nfe:dest/nfe:IE", doc));
        danfe.setIeSubstituto(text(xpath, "//nfe:dest/nfe:IEST", doc));
        danfe.setTelefoneDestinatario(text(xpath, "//nfe:dest/nfe:enderDest/nfe:fone", doc));
        danfe.setEnderecoDestinatario(montaEndereco(xpath, doc, "//nfe:dest/nfe:enderDest"));
        danfe.setCidadeDestinatario(montaCidadeUf(xpath, doc, "//nfe:dest/nfe:enderDest"));
        danfe.setBairroDestinatario(text(xpath, "//nfe:dest/nfe:enderDest/nfe:xBairro", doc));
        danfe.setCepDestinatario(formatCep(text(xpath, "//nfe:dest/nfe:enderDest/nfe:CEP", doc)));
        danfe.setUfDestinatario(text(xpath, "//nfe:dest/nfe:enderDest/nfe:UF", doc));

        // Produtos
        danfe.setProdutos(extrairProdutos(xpath, doc));

        // Duplicatas
        danfe.setDuplicatas(extrairDuplicatas(xpath, doc));

        // Transporte
        danfe.setModalidadeFrete(text(xpath, "//nfe:transp/nfe:modFrete", doc));
        danfe.setTransportadorNome(text(xpath, "//nfe:transp/nfe:transporta/nfe:xNome", doc));
        String cnpjTransp = text(xpath, "//nfe:transp/nfe:transporta/nfe:CNPJ", doc);
        String cpfTransp = text(xpath, "//nfe:transp/nfe:transporta/nfe:CPF", doc);
        danfe.setTransportadorCnpj(!cnpjTransp.isEmpty() ? formatCnpj(cnpjTransp) : formatCpf(cpfTransp));
        danfe.setTransportadorEndereco(text(xpath, "//nfe:transp/nfe:transporta/nfe:xEnder", doc));
        danfe.setTransportadorMunicipio(text(xpath, "//nfe:transp/nfe:transporta/nfe:xMun", doc));
        danfe.setTransportadorUf(text(xpath, "//nfe:transp/nfe:transporta/nfe:UF", doc));
        String antt = text(xpath, "//nfe:transp/nfe:veicTransp/nfe:RNTC", doc);
        if (antt.isEmpty()) {
            antt = text(xpath, "//nfe:transp/nfe:transporta/nfe:RNTC", doc);
        }
        danfe.setTransportadorCodigoAntt(antt);
        danfe.setVeiculoPlaca(text(xpath, "//nfe:transp/nfe:veicTransp/nfe:placa", doc));
        danfe.setVeiculoUf(text(xpath, "//nfe:transp/nfe:veicTransp/nfe:UF", doc));
        danfe.setVolumesQuantidade(text(xpath, "//nfe:transp/nfe:vol/nfe:qVol", doc));
        danfe.setVolumesEspecie(text(xpath, "//nfe:transp/nfe:vol/nfe:esp", doc));
        danfe.setVolumesMarca(text(xpath, "//nfe:transp/nfe:vol/nfe:marca", doc));
        danfe.setVolumesNumeracao(text(xpath, "//nfe:transp/nfe:vol/nfe:nVol", doc));
        danfe.setVolumesPesoBruto(text(xpath, "//nfe:transp/nfe:vol/nfe:pesoB", doc));
        danfe.setVolumesPesoLiquido(text(xpath, "//nfe:transp/nfe:vol/nfe:pesoL", doc));

        // ISSQN (quando existir)
        danfe.setValorTotalServicos(text(xpath, "//nfe:ISSQNtot/nfe:vServ", doc));
        danfe.setBaseCalculoIssqn(text(xpath, "//nfe:ISSQNtot/nfe:vBC", doc));
        danfe.setValorIssqn(text(xpath, "//nfe:ISSQNtot/nfe:vISS", doc));
        danfe.setAliquotaIssqn(text(xpath, "//nfe:ISSQNtot/nfe:vAliq", doc));

        // Informações adicionais
        danfe.setInformacoesComplementares(text(xpath, "//nfe:infAdic/nfe:infCpl", doc));
        danfe.setInformacoesFisco(text(xpath, "//nfe:infAdic/nfe:infAdFisco", doc));
        danfe.setFormaPagamento(extrairFormaPagamento(xpath, doc));

        // Datas de saída/entrada (se existirem)
        setDataSaidaHora(danfe, xpath, doc);

        // Protocolo (se existir)
        danfe.setProtocoloAutorizacao(nfe.getProtocolo());
        if (nfe.getDataEmissao() != null) {
            danfe.setDataAutorizacao(nfe.getDataEmissao().format(OUT_DATE_TIME));
        }

        return danfe;
    }

    private List<DanfeModel.Produto> extrairProdutos(XPath xpath, Document doc) throws Exception {
        List<DanfeModel.Produto> itens = new ArrayList<>();
        NodeList dets = (NodeList) xpath.evaluate("//nfe:det", doc, XPathConstants.NODESET);
        for (int i = 0; i < dets.getLength(); i++) {
            Element det = (Element) dets.item(i);
            DanfeModel.Produto p = new DanfeModel.Produto();
            p.setCodigo(text(xpath, "nfe:prod/nfe:cProd", det));
            p.setDescricao(text(xpath, "nfe:prod/nfe:xProd", det));
            p.setNcm(text(xpath, "nfe:prod/nfe:NCM", det));
            p.setCfop(text(xpath, "nfe:prod/nfe:CFOP", det));
            p.setQuantidade(text(xpath, "nfe:prod/nfe:qCom", det));
            p.setUnidade(text(xpath, "nfe:prod/nfe:uCom", det));
            p.setValorUnitario(text(xpath, "nfe:prod/nfe:vUnCom", det));
            p.setValorTotal(text(xpath, "nfe:prod/nfe:vProd", det));
            p.setValorDesconto(text(xpath, "nfe:prod/nfe:vDesc", det));
            p.setValorPis(text(xpath, "nfe:imposto//nfe:PIS//nfe:vPIS", det));
            p.setAliquotaPis(text(xpath, "nfe:imposto//nfe:PIS//nfe:pPIS", det));
            p.setValorCofins(text(xpath, "nfe:imposto//nfe:COFINS//nfe:vCOFINS", det));
            p.setAliquotaCofins(text(xpath, "nfe:imposto//nfe:COFINS//nfe:pCOFINS", det));
            p.setValorTotTrib(text(xpath, "nfe:imposto//nfe:vTotTrib", det));
            String cst = text(xpath, "nfe:imposto/nfe:ICMS/*/nfe:CST", det);
            if (cst.isEmpty()) {
                cst = text(xpath, "nfe:imposto/nfe:ICMS/*/nfe:CSOSN", det);
            }
            p.setCst(cst);
            p.setBaseCalculoIcms(text(xpath, "nfe:imposto/nfe:ICMS/*/nfe:vBC", det));
            p.setValorIcms(text(xpath, "nfe:imposto/nfe:ICMS/*/nfe:vICMS", det));
            p.setValorIpi(text(xpath, "nfe:imposto//nfe:vIPI", det));
            p.setAliquotaIcms(text(xpath, "nfe:imposto/nfe:ICMS/*/nfe:pICMS", det));
            p.setAliquotaIpi(text(xpath, "nfe:imposto//nfe:pIPI", det));
            itens.add(p);
        }
        return itens;
    }

    private String extrairFormaPagamento(XPath xpath, Document doc) throws Exception {
        NodeList detPagList = (NodeList) xpath.evaluate("//nfe:pag/nfe:detPag", doc, XPathConstants.NODESET);
        if (detPagList == null || detPagList.getLength() == 0) {
            return "";
        }
        List<String> formas = new ArrayList<>();
        for (int i = 0; i < detPagList.getLength(); i++) {
            if (!(detPagList.item(i) instanceof Element)) continue;
            Element detPag = (Element) detPagList.item(i);
            String tPag = text(xpath, "nfe:tPag", detPag);
            if (tPag == null || tPag.isBlank()) continue;
            String forma = converterFormaPagamento(tPag.trim());
            if (!forma.isBlank() && !formas.contains(forma)) {
                formas.add(forma);
            }
        }
        return String.join(" / ", formas);
    }

    private List<DanfeModel.Duplicata> extrairDuplicatas(XPath xpath, Document doc) throws Exception {
        List<DanfeModel.Duplicata> duplicatas = new ArrayList<>();
        NodeList dups = (NodeList) xpath.evaluate("//nfe:cobr/nfe:dup", doc, XPathConstants.NODESET);
        for (int i = 0; i < dups.getLength(); i++) {
            Element dup = (Element) dups.item(i);
            DanfeModel.Duplicata d = new DanfeModel.Duplicata();
            d.setNumero(text(xpath, "nfe:nDup", dup));
            d.setVencimento(formatDateOnly(text(xpath, "nfe:dVenc", dup)));
            d.setValor(text(xpath, "nfe:vDup", dup));
            duplicatas.add(d);
        }
        return duplicatas;
    }

    private String montaEndereco(XPath xpath, Document doc, String base) throws Exception {
        String logradouro = text(xpath, base + "/nfe:xLgr", doc);
        String numero = text(xpath, base + "/nfe:nro", doc);
        String bairro = text(xpath, base + "/nfe:xBairro", doc);
        String cep = text(xpath, base + "/nfe:CEP", doc);
        StringBuilder sb = new StringBuilder();
        if (!logradouro.isEmpty()) sb.append(logradouro);
        if (!numero.isEmpty()) sb.append(", ").append(numero);
        if (!bairro.isEmpty()) sb.append(" - ").append(bairro);
        if (!cep.isEmpty()) sb.append(" - CEP ").append(cep);
        return sb.toString();
    }

    private String montaCidadeUf(XPath xpath, Document doc, String base) throws Exception {
        String cidade = text(xpath, base + "/nfe:xMun", doc);
        String uf = text(xpath, base + "/nfe:UF", doc);
        if (cidade.isEmpty() && uf.isEmpty()) return "";
        return cidade + (uf.isEmpty() ? "" : " - " + uf);
    }

    private String formatDhEmi(String dhEmi) {
        if (dhEmi == null || dhEmi.isBlank()) return "";
        try {
            // Ex.: 2025-01-31T10:30:45-03:00
            String base = dhEmi.length() >= 19 ? dhEmi.substring(0, 19) : dhEmi;
            LocalDateTime dt = LocalDateTime.parse(base);
            return OUT_DATE_TIME.format(dt);
        } catch (Exception e) {
            return dhEmi;
        }
    }

    private String mapAmbiente(String tpAmb) {
        if (tpAmb == null) return "";
        String value = tpAmb.trim();
        if ("2".equals(value)) return "HOMOLOGACAO";
        if ("1".equals(value)) return "PRODUCAO";
        return value;
    }

    private String formatCnpj(String cnpj) {
        if (cnpj == null) return null;
        String digits = cnpj.replaceAll("\\D", "");
        if (digits.length() != 14) return cnpj;
        return digits.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    private String formatCpf(String cpf) {
        if (cpf == null) return null;
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) return cpf;
        return digits.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }

    private String formatCep(String cep) {
        if (cep == null) return "";
        String digits = cep.replaceAll("\\D", "");
        if (digits.length() != 8) return cep;
        return digits.replaceFirst("(\\d{5})(\\d{3})", "$1-$2");
    }

    private String formatDateOnly(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            if (value.length() >= 19) {
                String base = value.substring(0, 19);
                LocalDateTime dt = LocalDateTime.parse(base);
                return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
            if (value.length() >= 10) {
                String base = value.substring(0, 10);
                return java.time.LocalDate.parse(base).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
            return value;
        }
        return value;
    }

    private String formatTimeOnly(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            if (value.length() >= 19) {
                String base = value.substring(0, 19);
                LocalDateTime dt = LocalDateTime.parse(base);
                return dt.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            }
        } catch (Exception e) {
            return value;
        }
        return value;
    }

    private String converterFormaPagamento(String tPag) {
        if (tPag == null) return "";
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

    private void setDataSaidaHora(DanfeModel danfe, XPath xpath, Document doc) throws Exception {
        String dhSaiEnt = text(xpath, "//nfe:ide/nfe:dhSaiEnt", doc);
        if (!dhSaiEnt.isEmpty()) {
            danfe.setDataSaida(formatDateOnly(dhSaiEnt));
            danfe.setHoraSaida(formatTimeOnly(dhSaiEnt));
            return;
        }
        String dSaiEnt = text(xpath, "//nfe:ide/nfe:dSaiEnt", doc);
        String hSaiEnt = text(xpath, "//nfe:ide/nfe:hSaiEnt", doc);
        if (!dSaiEnt.isEmpty()) {
            danfe.setDataSaida(formatDateOnly(dSaiEnt));
        }
        if (!hSaiEnt.isEmpty()) {
            danfe.setHoraSaida(hSaiEnt);
        }
    }

    private Document buildDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private XPath buildXPath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
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
        return xpath;
    }

    private String text(XPath xpath, String expr, Object node) throws Exception {
        String value = xpath.evaluate(expr, node);
        return value != null ? value.trim() : "";
    }
}
