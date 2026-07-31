package com.baronesa.emporio.nfe.builder;

import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TEnviNFe;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNFe;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNFe.InfNFe;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNFe.InfNFe.Det;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNFe.InfNFe.Pag;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNFe.InfNFe.Transp;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNFe.InfNFe.Total;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNFe.InfNFe.Total.ICMSTot;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TRetEnviNFe;
import br.com.swconsultoria.nfe.util.ChaveUtil;
import br.com.swconsultoria.nfe.util.ConstantesUtil;
import br.com.swconsultoria.nfe.util.NFCeUtil;
import br.com.swconsultoria.nfe.util.WebServiceUtil;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import com.baronesa.emporio.entity.NfeModel;
import com.baronesa.emporio.enums.StatusNfe;
import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.nfe.model.Venda;
import com.baronesa.emporio.nfe.model.VendaItem;
import com.baronesa.emporio.nfe.model.VendaPagamento;
import com.baronesa.emporio.nfe.service.CertificadoDigitalService;
import com.baronesa.emporio.nfe.service.EmitenteNFeService;
import com.baronesa.emporio.repository.NfeRepository;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class XmlNFCeBuilder {

    private static final String HOMOLOGACAO_XNOME =
            "NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";

    private final CertificadoDigitalService certificadoService;
    private final ConfigManager configManager;
    private final NfeRepository nfeRepository;
    private final EmitenteNFeService emitenteNFeService;

    public NfeModel gerarNFCeCompleta(Long pagamentoId, Venda venda) throws Exception {
        return gerarNFCeCompleta(pagamentoId, venda, null);
    }

    public NfeModel gerarNFCeCompleta(Long pagamentoId, Venda venda, String cpfConsumidor) throws Exception {
        ConfiguracoesNfe config = certificadoService.inicializarConfiguracoesSefazNfce();

        int numeroNFCe = configManager.getIntConfig("nfce_numero", 1);
        String cnpj = configManager.getConfig("nfe_cnpj", "");
        ZonedDateTime agora = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime dataEmissao = agora.toLocalDateTime();

        String cnf = String.format("%08d", new Random().nextInt(99_999_999));
        String modelo = DocumentoEnum.NFCE.getModelo();
        int serie = Integer.parseInt(configManager.getConfig("nfce_serie", "1"));
        String tipoEmissao = "1";

        ChaveUtil chaveUtil = new ChaveUtil(config.getEstado(), cnpj, modelo, serie, numeroNFCe, tipoEmissao, cnf, dataEmissao);
        String chave = chaveUtil.getChaveNF();
        String cdv = chaveUtil.getDigitoVerificador();

        InfNFe infNFe = new InfNFe();
        // A tag Id deve conter exatamente um prefixo "NFe" (não duplicar)
        String idInfNfe = chave.startsWith("NFe") ? chave : "NFe" + chave;
        infNFe.setId(idInfNfe);
        infNFe.setVersao(ConstantesUtil.VERSAO.NFE);
        infNFe.setIde(criarIde(config, cnf, numeroNFCe, tipoEmissao, modelo, serie, cdv, agora));
        infNFe.setEmit(emitenteNFeService.criarDadosEmitente());

        TNFe.InfNFe.Dest dest = criarDestinatario(
                venda,
                !"1".equals(config.getAmbiente().getCodigo()),
                cpfConsumidor
        );
        if (dest != null) {
            infNFe.setDest(dest);
        }

        List<Det> itens = criarItens(venda, !"1".equals(config.getAmbiente().getCodigo()));
        infNFe.getDet().addAll(itens);

        infNFe.setTotal(criarTotais(venda));
        infNFe.setTransp(criarTransporte());
        infNFe.setPag(criarPagamento(venda));

        TNFe nfe = new TNFe();
        nfe.setInfNFe(infNFe);

        TEnviNFe enviNFe = new TEnviNFe();
        enviNFe.setVersao(ConstantesUtil.VERSAO.NFE);
        enviNFe.setIdLote("1");
        enviNFe.setIndSinc("1");
        enviNFe.getNFe().add(nfe);

        TEnviNFe enviNFeAssinado;
        try {
            enviNFeAssinado = Nfe.montaNfe(config, enviNFe, true);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log.error("Erro ao montar NFC-e: msg={}, causa={}", e.getMessage(), root.getMessage(), e);
            throw e;
        }

        String qrCode = NFCeUtil.getCodeQRCodeV3(
                chave.substring(3),
                config.getAmbiente().getCodigo(),
                WebServiceUtil.getUrl(config, DocumentoEnum.NFCE, br.com.swconsultoria.nfe.dom.enuns.ServicosEnum.URL_QRCODE)
        );

        TNFe.InfNFeSupl infNFeSupl = new TNFe.InfNFeSupl();
        infNFeSupl.setQrCode(qrCode);
        infNFeSupl.setUrlChave(WebServiceUtil.getUrl(config, DocumentoEnum.NFCE, br.com.swconsultoria.nfe.dom.enuns.ServicosEnum.URL_CONSULTANFCE));
        enviNFeAssinado.getNFe().get(0).setInfNFeSupl(infNFeSupl);

        var retorno = Nfe.enviarNfe(config, enviNFeAssinado, DocumentoEnum.NFCE);

        NfeModel nfeModel = new NfeModel();
        nfeModel.setIdVenda(venda.getId());
        nfeModel.setChaveAcesso(chave);
        nfeModel.setNumero(String.valueOf(numeroNFCe));
        nfeModel.setSerie(String.valueOf(serie));
        nfeModel.setModelo(65);
        nfeModel.setDataEmissao(agora.toLocalDateTime());
        nfeModel.setValorTotal(venda.getValorTotal() != null ? venda.getValorTotal().doubleValue() : 0d);
        nfeModel.setAmbiente("1".equals(config.getAmbiente().getCodigo()) ? 1 : 2);

        String xmlFinal = XmlNfeUtil.objectToXml(enviNFeAssinado);
        nfeModel.setXmlAssinado(xmlFinal);

        String xmlRetorno = XmlNfeUtil.objectToXml(retorno);
        nfeModel.setXmlRetorno(xmlRetorno);

        processarRetorno(retorno, nfeModel);

        configManager.setConfig("nfce_numero", String.valueOf(numeroNFCe + 1));

        NfeModel salvo = nfeRepository.save(nfeModel);
        log.info("NFCe {} salva com status {}", salvo.getNumero(), salvo.getStatus());
        return salvo;
    }

    private TNFe.InfNFe.Ide criarIde(ConfiguracoesNfe config, String cnf, int numeroNFCe,
                                     String tipoEmissao, String modelo, int serie, String cdv,
                                     ZonedDateTime agora) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(config.getEstado().getCodigoUF());
        ide.setCNF(cnf);
        ide.setNatOp("VENDA");
        ide.setMod(modelo);
        ide.setSerie(String.valueOf(serie));
        ide.setNNF(String.valueOf(numeroNFCe));
        ide.setDhEmi(XmlNfeUtil.dataNfe(agora.toLocalDateTime(), null));
        ide.setTpNF("1");
        ide.setIdDest("1");
        ide.setCMunFG(configManager.getConfig("nfe_cod_municipio", "3552205"));
        ide.setTpImp("4");
        ide.setTpEmis(tipoEmissao);
        ide.setCDV(cdv);
        ide.setTpAmb(config.getAmbiente().getCodigo());
        ide.setFinNFe("1");
        ide.setIndFinal("1");
        ide.setIndPres("1");
        ide.setProcEmi("0");
        ide.setVerProc("NFCe Villa");
        return ide;
    }

    private TNFe.InfNFe.Dest criarDestinatario(Venda venda, boolean homologacao, String cpfConsumidor) {
        if (venda.getCliente() == null) {
            if (cpfConsumidor == null || cpfConsumidor.isBlank()) {
                return null;
            }
            TNFe.InfNFe.Dest dest = new TNFe.InfNFe.Dest();
            dest.setCPF(cpfConsumidor.replaceAll("[^0-9]", ""));
            if (homologacao) {
                dest.setXNome(HOMOLOGACAO_XNOME);
            }
            dest.setIndIEDest("9");
            return dest;
        }

        var perfil = venda.getCliente().getPerfilCliente();
        if (perfil == null) {
            return null;
        }

        String cpf = perfil.getCpf();
        String cnpj = perfil.getCnpj();
        if ((cpf == null || cpf.isBlank()) && (cnpj == null || cnpj.isBlank())) {
            return null;
        }

        TNFe.InfNFe.Dest dest = new TNFe.InfNFe.Dest();
        if (cpf != null && !cpf.isBlank()) {
            dest.setCPF(cpf.replaceAll("[^0-9]", ""));
        } else {
            dest.setCNPJ(cnpj.replaceAll("[^0-9]", ""));
        }

        if (homologacao) {
            dest.setXNome(HOMOLOGACAO_XNOME);
        } else {
            dest.setXNome(venda.getCliente().getNome());
        }
        dest.setIndIEDest("9");
        return dest;
    }

    private List<Det> criarItens(Venda venda, boolean homologacao) {
        List<Det> dets = new java.util.ArrayList<>();
        BigDecimal acrescimo = venda.getAcrescimoTotal() != null ? venda.getAcrescimoTotal() : BigDecimal.ZERO;
        BigDecimal totalItens = venda.getSubtotal() != null ? venda.getSubtotal() : BigDecimal.ZERO;
        int item = 1;
        for (VendaItem vendaItem : venda.getItens()) {
            Det det = new Det();
            det.setNItem(String.valueOf(item++));

            var prod = new Det.Prod();
            prod.setCProd(vendaItem.getCodigoProduto());
            prod.setCEAN("SEM GTIN");
            String descricao = homologacao
                    ? "NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL"
                    : vendaItem.getDescricaoProduto();
            prod.setXProd(descricao);
            prod.setNCM(vendaItem.getNcm());
            prod.setCFOP(vendaItem.getCfop());
            prod.setUCom("UN");
            prod.setQCom(vendaItem.getQuantidade().toString());
            prod.setVUnCom(vendaItem.getValorUnitario().toString());
            prod.setVProd(vendaItem.getValorTotalSeguro().toString());
            // Distribui vOutro proporcionalmente aos itens para fechar com o total
            if (acrescimo.compareTo(BigDecimal.ZERO) > 0 && totalItens.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal proporcao = vendaItem.getValorTotalSeguro().divide(totalItens, 6, java.math.RoundingMode.HALF_UP);
                BigDecimal vOutroItem = acrescimo.multiply(proporcao).setScale(2, java.math.RoundingMode.HALF_UP);
                prod.setVOutro(vOutroItem.toPlainString());
            }
            prod.setCEANTrib("SEM GTIN");
            prod.setUTrib("UN");
            prod.setQTrib(vendaItem.getQuantidade().toString());
            prod.setVUnTrib(vendaItem.getValorUnitario().toString());
            prod.setIndTot("1");

            det.setProd(prod);
            det.setImposto(criarImpostos());
            dets.add(det);
        }
        return dets;
    }

    private TNFe.InfNFe.Det.Imposto criarImpostos() {
        TNFe.InfNFe.Det.Imposto imposto = new TNFe.InfNFe.Det.Imposto();

        TNFe.InfNFe.Det.Imposto.ICMS icms = new TNFe.InfNFe.Det.Imposto.ICMS();
        TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102 sn102 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102();
        sn102.setOrig("0");
        sn102.setCSOSN("102");
        icms.setICMSSN102(sn102);
        imposto.getContent().add(new JAXBElement<>(new QName("ICMS"), TNFe.InfNFe.Det.Imposto.ICMS.class, icms));

        TNFe.InfNFe.Det.Imposto.PIS pis = new TNFe.InfNFe.Det.Imposto.PIS();
        TNFe.InfNFe.Det.Imposto.PIS.PISNT pisnt = new TNFe.InfNFe.Det.Imposto.PIS.PISNT();
        pisnt.setCST("07");
        pis.setPISNT(pisnt);
        imposto.getContent().add(new JAXBElement<>(new QName("PIS"), TNFe.InfNFe.Det.Imposto.PIS.class, pis));

        TNFe.InfNFe.Det.Imposto.COFINS cofins = new TNFe.InfNFe.Det.Imposto.COFINS();
        TNFe.InfNFe.Det.Imposto.COFINS.COFINSNT cofinsnt = new TNFe.InfNFe.Det.Imposto.COFINS.COFINSNT();
        cofinsnt.setCST("07");
        cofins.setCOFINSNT(cofinsnt);
        imposto.getContent().add(new JAXBElement<>(new QName("COFINS"), TNFe.InfNFe.Det.Imposto.COFINS.class, cofins));

        return imposto;
    }

    private Total criarTotais(Venda venda) {
        Total total = new Total();
        ICMSTot icmsTot = new ICMSTot();

        BigDecimal valorProdutos = venda.getSubtotal();
        BigDecimal valorNF = venda.getValorTotal() != null ? venda.getValorTotal() : valorProdutos;
        BigDecimal outros = venda.getAcrescimoTotal() != null ? venda.getAcrescimoTotal() : BigDecimal.ZERO;
        BigDecimal desconto = venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO;

        icmsTot.setVBC("0.00");
        icmsTot.setVICMS("0.00");
        icmsTot.setVICMSDeson("0.00");
        icmsTot.setVFCP("0.00");
        icmsTot.setVFCPST("0.00");
        icmsTot.setVFCPSTRet("0.00");
        icmsTot.setVBCST("0.00");
        icmsTot.setVST("0.00");
        icmsTot.setVProd(valorProdutos.toPlainString());
        icmsTot.setVFrete("0.00");
        icmsTot.setVSeg("0.00");
        icmsTot.setVDesc(desconto.toPlainString());
        icmsTot.setVII("0.00");
        icmsTot.setVIPI("0.00");
        icmsTot.setVIPIDevol("0.00");
        icmsTot.setVPIS("0.00");
        icmsTot.setVCOFINS("0.00");
        icmsTot.setVOutro(outros.toPlainString());
        icmsTot.setVNF(valorNF.toPlainString());

        total.setICMSTot(icmsTot);
        return total;
    }

    private Transp criarTransporte() {
        Transp transp = new Transp();
        transp.setModFrete("9");
        return transp;
    }

    private Pag criarPagamento(Venda venda) {
        Pag pag = new Pag();
        for (VendaPagamento vp : venda.getPagamentos()) {
            Pag.DetPag detPag = new Pag.DetPag();
            detPag.setTPag(vp.getTipoPagamento().getCodigo());
            detPag.setVPag(vp.getValorSeguro().toPlainString());
            if (vp.getTipoPagamento() == TipoFormaPagamento.PIX
                    || vp.getTipoPagamento() == TipoFormaPagamento.CARTAO_CREDITO
                    || vp.getTipoPagamento() == TipoFormaPagamento.CARTAO_DEBITO) {
                Pag.DetPag.Card card = new Pag.DetPag.Card();
                card.setTpIntegra("2");
                card.setTBand("99");
                detPag.setCard(card);
            }
            pag.getDetPag().add(detPag);
        }
        return pag;
    }

    private void processarRetorno(TRetEnviNFe retorno, NfeModel nfeModel) {
        if ("104".equals(retorno.getCStat())) {
            if (retorno.getProtNFe() != null && "100".equals(retorno.getProtNFe().getInfProt().getCStat())) {
                nfeModel.setStatus("AUTORIZADA");
                nfeModel.setProtocolo(retorno.getProtNFe().getInfProt().getNProt());
            } else {
                nfeModel.setStatus("REJEITADA");
                if (retorno.getProtNFe() != null) {
                    nfeModel.setMotivoRejeicao(retorno.getProtNFe().getInfProt().getXMotivo());
                }
            }
        } else {
            nfeModel.setStatus("ERRO_PROCESSAMENTO");
            nfeModel.setMotivoRejeicao(retorno.getXMotivo());
        }
    }
}
