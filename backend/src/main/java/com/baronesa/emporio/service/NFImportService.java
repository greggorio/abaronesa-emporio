package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.Fornecedor;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.repository.FornecedorRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NFImportService {

    private final FornecedorRepository fornecedorRepository;
    private final ProdutoRepository produtoRepository;

    public ImportacaoNfeDTO parseXml(MultipartFile arquivo) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(arquivo.getInputStream());
            doc.getDocumentElement().normalize();

            // Extrair dados da NFe
            Element nfeProc = doc.getDocumentElement();
            Element nfe = getElement(nfeProc, "NFe");
            Element infNFe = getElement(nfe, "infNFe");

            // Dados da nota
            Element ide = getElement(infNFe, "ide");
            String numeroNf = getElementValue(ide, "nNF");
            String chaveNfe = infNFe.getAttribute("Id").replace("NFe", "");
            LocalDate dataEmissao = LocalDate.parse(
                    getElementValue(ide, "dhEmi").substring(0, 10)
            );

            // Dados do fornecedor
            Element emit = getElement(infNFe, "emit");
            FornecedorNfeDTO fornecedor = extrairFornecedor(emit);

            // Itens da nota
            NodeList detNodes = infNFe.getElementsByTagName("det");
            List<ItemNfeDTO> itens = new ArrayList<>();
            BigDecimal valorTotal = BigDecimal.ZERO;

            for (int i = 0; i < detNodes.getLength(); i++) {
                Element det = (Element) detNodes.item(i);
                ItemNfeDTO item = extrairItem(det);
                itens.add(item);
                valorTotal = valorTotal.add(item.valorTotal());
            }

            // Montar resposta
            ImportacaoNfeDTO resultado = new ImportacaoNfeDTO(
                    numeroNf,
                    chaveNfe,
                    dataEmissao,
                    fornecedor,
                    itens,
                    valorTotal
            );

            log.info("NF-e {} processada com sucesso. {} itens encontrados", numeroNf, itens.size());

            return resultado;

        } catch (Exception e) {
            log.error("Erro ao processar XML da NF-e", e);
            throw new RuntimeException("Erro ao processar XML: " + e.getMessage());
        }
    }

    private FornecedorNfeDTO extrairFornecedor(Element emit) {
        String cnpj = getElementValue(emit, "CNPJ");
        String razaoSocial = getElementValue(emit, "xNome");
        String nomeFantasia = getElementValue(emit, "xFant");

        // Verificar se fornecedor já existe
        Optional<Fornecedor> fornecedorExistente = fornecedorRepository.findByCnpj(cnpj);

        return new FornecedorNfeDTO(
                cnpj,
                razaoSocial,
                nomeFantasia != null ? nomeFantasia : razaoSocial,
                fornecedorExistente.map(Fornecedor::getId).orElse(null),
                fornecedorExistente.isPresent()
        );
    }

    private ItemNfeDTO extrairItem(Element det) {
        Element prod = getElement(det, "prod");
        Element imposto = getElement(det, "imposto");

        String codigo = getElementValue(prod, "cProd");
        String descricao = getElementValue(prod, "xProd");
        String ncm = getElementValue(prod, "NCM");
        String cfop = getElementValue(prod, "CFOP");
        String unidade = getElementValue(prod, "uCom");

        BigDecimal quantidade = new BigDecimal(getElementValue(prod, "qCom"));
        BigDecimal valorUnitario = new BigDecimal(getElementValue(prod, "vUnCom"));
        BigDecimal valorTotal = new BigDecimal(getElementValue(prod, "vProd"));

        // Verificar se produto existe pelo código do fornecedor
        Optional<Produto> produtoExistente = produtoRepository.findByCodigoFornecedor(codigo);

        return new ItemNfeDTO(
                codigo,
                descricao,
                ncm,
                cfop,
                unidade,
                quantidade,
                valorUnitario,
                valorTotal,
                produtoExistente.map(Produto::getId).orElse(null),
                produtoExistente.isPresent()
        );
    }

    // Métodos auxiliares para XML
    private Element getElement(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return (Element) nodeList.item(0);
        }
        return null;
    }

    private String getElementValue(Element parent, String tagName) {
        Element element = getElement(parent, tagName);
        if (element != null) {
            return element.getTextContent();
        }
        return null;
    }

    public ImportacaoNfeResponse validarImportacao(ImportacaoNfeDTO dados) {
        List<String> avisos = new ArrayList<>();
        List<String> erros = new ArrayList<>();

        // Validar fornecedor
        if (!dados.fornecedor().cadastrado()) {
            avisos.add("Fornecedor não cadastrado: " + dados.fornecedor().razaoSocial());
        }

        // Validar produtos
        int produtosNaoCadastrados = 0;
        for (ItemNfeDTO item : dados.itens()) {
            if (!item.cadastrado()) {
                produtosNaoCadastrados++;
            }
        }

        if (produtosNaoCadastrados > 0) {
            avisos.add(produtosNaoCadastrados + " produto(s) não cadastrado(s)");
        }

        // Verificar se NF já existe
        // TODO: Implementar verificação de NF duplicada

        return new ImportacaoNfeResponse(
                erros.isEmpty(),
                dados,
                avisos,
                erros
        );
    }
}