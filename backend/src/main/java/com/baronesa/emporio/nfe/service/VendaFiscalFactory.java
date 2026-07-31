package com.baronesa.emporio.nfe.service;

import com.baronesa.emporio.entity.ItemPedido;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoFiscal;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.PagamentoAlocacao;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.enums.ModalidadeFrete;
import com.baronesa.emporio.enums.OrigemVenda;
import com.baronesa.emporio.enums.StatusPagamento;
import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.enums.StatusNfe;
import com.baronesa.emporio.enums.StatusVenda;
import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.nfe.model.Venda;
import com.baronesa.emporio.nfe.model.VendaEntrega;
import com.baronesa.emporio.nfe.model.VendaItem;
import com.baronesa.emporio.nfe.model.VendaPagamento;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.repository.PagamentoAlocacaoRepository;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Constrói objetos {@link Venda} a partir de pagamentos realizados no restaurante.
 * Cada pagamento confirmado gera uma "mini venda" compatível com o fluxo da NFC-e.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendaFiscalFactory {

    private final PagamentoRepository pagamentoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PagamentoAlocacaoRepository pagamentoAlocacaoRepository;
    private final ConfigManager configManager;

    private static final String CFOP_DEFAULT = "5102";

    /**
     * Monta um objeto {@link Venda} para o pagamento informado.
     *
     * @param pagamentoId identificador do pagamento (mesa ou convidado)
     * @return venda consolidada para emissão da NFC-e
     */
    public Venda criarVendaParaPagamento(Long pagamentoId) {
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado: " + pagamentoId));
        return criarVendaParaPagamento(pagamento);
    }

    /**
     * Monta o objeto de venda a partir de um pagamento já carregado.
     */
    public Venda criarVendaParaPagamento(Pagamento pagamento) {
        SessaoMesa sessaoMesa = pagamento.getSessaoMesa();
        List<Pagamento> pagamentosLote = resolverPagamentosLote(pagamento);
        List<ItemPedido> itensFonte = carregarItensParaPagamento(pagamento);
        List<VendaItem> itens = itensFonte.stream()
                .filter(item -> item.getStatus() != StatusItem.CANCELED)
                .map(this::mapearItem)
                .collect(Collectors.toList());

        BigDecimal subtotal = itens.stream()
                .map(VendaItem::getValorTotalSeguro)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxaServico = BigDecimal.ZERO;
        for (Pagamento pagamentoLote : pagamentosLote) {
            if (pagamentoLote.getValorTaxaServico() != null) {
                taxaServico = taxaServico.add(pagamentoLote.getValorTaxaServico());
            }
        }

        Venda venda = Venda.builder()
                .id(pagamento.getId())
                .codigo(gerarCodigoVenda(pagamento))
                .origem(OrigemVenda.LOJA_FISICA)
                .cliente(extrairCliente(pagamento))
                .dataVenda(pagamento.getPagoEm() != null ? pagamento.getPagoEm() : LocalDateTime.now())
                .valorTotal(subtotal.add(taxaServico))
                .descontoTotal(BigDecimal.ZERO)
                .acrescimoTotal(taxaServico)
                .valorFrete(BigDecimal.ZERO)
                .modalidadeFrete(ModalidadeFrete.SEM_FRETE)
                .status(StatusVenda.CONFIRMADA)
                .statusNfe(StatusNfe.NAO_EMITIDA)
                .observacoes(montarObservacoes(pagamento, taxaServico))
                .gatewayPagamento(pagamento.getMetodo())
                .idPagamentoGateway(pagamento.getProviderRef())
                .itens(new java.util.ArrayList<>(itens))
                .pagamentos(pagamentosLote.stream()
                        .map(this::mapearPagamento)
                        .collect(Collectors.toCollection(java.util.ArrayList::new)))
                .build();

        if (sessaoMesa != null) {
            venda.setObservacoesInternas("Mesa " + obterRotuloMesa(sessaoMesa));
        }

        // Entrega não é usada em mesa, mas mantemos objeto para compatibilidade
        VendaEntrega entrega = VendaEntrega.builder()
                .valorFrete(BigDecimal.ZERO)
                .valorTotalEnvio(BigDecimal.ZERO)
                .build();
        venda.definirEntrega(entrega);

        return venda;
    }

    private List<Pagamento> resolverPagamentosLote(Pagamento pagamento) {
        String ref = pagamento.getProviderRef();
        if (ref == null || !ref.startsWith("admin-multi-")) {
            return List.of(pagamento);
        }
        List<Pagamento> lote = pagamentoRepository.findByProviderRef(ref);
        if (lote == null || lote.isEmpty()) {
            return List.of(pagamento);
        }
        List<Pagamento> pagos = lote.stream()
                .filter(p -> p.getStatus() == StatusPagamento.PAID)
                .sorted(Comparator.comparing(Pagamento::getId))
                .collect(Collectors.toList());
        return pagos.isEmpty() ? List.of(pagamento) : pagos;
    }

    private List<ItemPedido> carregarItensParaPagamento(Pagamento pagamento) {
        SessaoConvidado convidado = pagamento.getSessaoConvidado();
        if (convidado != null) {
            return itemPedidoRepository.findByPedido_SessaoConvidado(convidado);
        }

        List<PagamentoAlocacao> alocacoes = pagamentoAlocacaoRepository.findByPagamento(pagamento);
        if (alocacoes != null && !alocacoes.isEmpty()) {
            List<SessaoConvidado> convidados =
                    alocacoes.stream()
                            .map(PagamentoAlocacao::getSessaoConvidado)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());
            if (!convidados.isEmpty()) {
                return itemPedidoRepository.findByPedido_SessaoConvidadoIn(convidados);
            }
        }

        return itemPedidoRepository.findByPedido_SessaoMesa(pagamento.getSessaoMesa());
    }

    private VendaItem mapearItem(ItemPedido item) {
        Produto produto = item.getProduto();
        ProdutoSKU sku = item.getSku();
        ProdutoFiscal fiscal = produto != null ? produto.getProdutoFiscal() : null;

        String descricao = produto != null ? produto.getNome() : "ITEM";
        if (sku != null && sku.getVariacao() != null && !sku.getVariacao().isBlank()) {
            descricao = descricao + " (" + sku.getVariacao() + ")";
        }

        // Definição do código do produto (Prioridade: EAN SKU -> EAN Produto -> SKU -> Código Interno -> ID)
        String codigoProduto = null;

        if (sku != null && sku.getCodigoBarras() != null && !sku.getCodigoBarras().isBlank()) {
            codigoProduto = sku.getCodigoBarras();
        }
        
        if (codigoProduto == null && produto != null && produto.getCodigoBarras() != null && !produto.getCodigoBarras().isBlank()) {
            codigoProduto = produto.getCodigoBarras();
        }
        
        if (codigoProduto == null && sku != null && sku.getSku() != null && !sku.getSku().isBlank()) {
            codigoProduto = sku.getSku();
        }
        
        if (codigoProduto == null && produto != null && produto.getCodigoInterno() != null && !produto.getCodigoInterno().isBlank()) {
            codigoProduto = produto.getCodigoInterno();
        }
        
        if (codigoProduto == null && produto != null) {
            codigoProduto = String.valueOf(produto.getId());
        }
        
        if (codigoProduto == null) {
            codigoProduto = String.valueOf(item.getId());
        }

        String cfop = fiscal != null && fiscal.getCfop() != null
                ? fiscal.getCfop()
                : configManager.getConfig("nfe_cfop_padrao", CFOP_DEFAULT);

        String ncm = fiscal != null ? fiscal.getNcm() : null;
        if (ncm == null || ncm.isBlank()) {
            ncm = "00000000";
        }

        String cst = fiscal != null ? fiscal.getSituacaoTributaria() : null;
        if (cst == null || cst.isBlank()) {
            cst = "102"; // Simples Nacional sem crédito como padrão
        }

        BigDecimal quantidade = BigDecimal.valueOf(item.getQuantidade());
        BigDecimal valorUnitario = item.getPrecoUnitario();
        BigDecimal valorTotal = valorUnitario.multiply(quantidade);

        return VendaItem.builder()
                .id(item.getId())
                .codigoProduto(codigoProduto)
                .descricaoProduto(descricao)
                .quantidade(quantidade)
                .valorUnitario(valorUnitario)
                .valorTotal(valorTotal)
                .cfop(cfop)
                .ncm(ncm)
                .cst(cst)
                .build();
    }

    private VendaPagamento mapearPagamento(Pagamento pagamento) {
        BigDecimal valorBase = pagamento.getValorBase() != null
                ? pagamento.getValorBase()
                : pagamento.getValor();
        BigDecimal valorPago = pagamento.getValor() != null
                ? pagamento.getValor()
                : valorBase;

        return VendaPagamento.builder()
                .id(pagamento.getId())
                .tipoPagamento(mapearFormaPagamento(pagamento.getMetodo(), pagamento.getCartaoTipo()))
                .valor(valorPago)
                .dataPagamento(pagamento.getPagoEm())
                .chavePix(pagamento.getQrPayload())
                .codigoAutorizacao(pagamento.getProviderRef())
                .build();
    }

    private TipoFormaPagamento mapearFormaPagamento(String metodo, String cartaoTipo) {
        if (metodo == null) {
            return TipoFormaPagamento.OUTROS;
        }
        return switch (metodo.toLowerCase(Locale.ROOT)) {
            case "pix" -> TipoFormaPagamento.PIX;
            case "card" -> isCartaoDebito(cartaoTipo) ? TipoFormaPagamento.CARTAO_DEBITO : TipoFormaPagamento.CARTAO_CREDITO;
            case "cash" -> TipoFormaPagamento.DINHEIRO;
            case "voucher" -> TipoFormaPagamento.VOUCHER;
            default -> TipoFormaPagamento.OUTROS;
        };
    }

    private boolean isCartaoDebito(String cartaoTipo) {
        if (cartaoTipo == null) return false;
        String normalized = cartaoTipo.toLowerCase(Locale.ROOT);
        return normalized.contains("debito") || normalized.contains("debit");
    }

    private com.baronesa.emporio.entity.Usuario extrairCliente(Pagamento pagamento) {
        SessaoConvidado beneficiario = pagamento.getSessaoConvidado();
        if (beneficiario != null && beneficiario.getUsuario() != null) {
            return beneficiario.getUsuario();
        }
        SessaoConvidado pagante = pagamento.getPagante();
        if (pagante != null && pagante.getUsuario() != null) {
            return pagante.getUsuario();
        }
        return null;
    }

    private String gerarCodigoVenda(Pagamento pagamento) {
        String prefixo = pagamento.getSessaoMesa() != null
                ? "MESA-" + obterRotuloMesa(pagamento.getSessaoMesa())
                : "PAG";
        return prefixo + "-" + pagamento.getId();
    }

    private String obterRotuloMesa(SessaoMesa sessaoMesa) {
        return sessaoMesa.getMesa() != null ? sessaoMesa.getMesa().getRotulo() : String.valueOf(sessaoMesa.getId());
    }

    private String montarObservacoes(Pagamento pagamento, BigDecimal taxaServico) {
        StringBuilder obs = new StringBuilder();
        obs.append("Pagamento ").append(pagamento.getId());
        if (pagamento.getSessaoMesa() != null && pagamento.getSessaoMesa().getMesa() != null) {
            obs.append(" - Mesa ").append(pagamento.getSessaoMesa().getMesa().getRotulo());
        }
        if (pagamento.getSessaoConvidado() != null) {
            obs.append(" - Convidado ").append(pagamento.getSessaoConvidado().getNomeExibicao());
        }
        if (taxaServico.compareTo(BigDecimal.ZERO) > 0) {
            obs.append(" | Inclui taxa de serviço R$ ").append(String.format(Locale.ROOT, "%.2f", taxaServico));
        }
        return obs.toString();
    }
}
