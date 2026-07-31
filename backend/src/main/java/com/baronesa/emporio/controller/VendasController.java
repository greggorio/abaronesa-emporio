package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.PagamentoListService;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map; // Re-added import
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime; // Added
import java.math.BigDecimal; // Added

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.baronesa.emporio.dto.SalesRecordDTO;
import com.baronesa.emporio.dto.PaginatedSalesResponse;

import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.entity.ItemPedido;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.entity.PagamentoAlocacao;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.repository.PagamentoAlocacaoRepository;

@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
public class VendasController extends BaseListController<PagamentoListService> {

    private final PagamentoListService listService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService;
    private final DynamicFormRegistry formRegistry;
    private final PagamentoRepository pagamentoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PagamentoAlocacaoRepository pagamentoAlocacaoRepository;

    @Override
    protected PagamentoListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    /**
     * Endpoint de compatibilidade: retorna configuração de formulário quando existir.
     * Se não houver definição dinâmica para "vendas", retorna apenas os dados paginados.
     */
    @GetMapping("/form-config")
    public ResponseEntity<Map<String, Object>> getFormConfig(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho,
            @RequestParam(required = false) String ordenacao,
            @RequestParam(required = false) String direcao,
            @RequestParam(required = false) String filter) {

        if (formRegistry.hasConfig("vendas")) {
            return formConfigService.processFormConfig("vendas", listService,
                    pagina, tamanho, ordenacao, direcao, filter);
        }

        // Fallback simples sem definição dinâmica
        Map<String, Object> body = listService.list(pagina, tamanho, ordenacao, direcao, filter, messageResolver);
        return ResponseEntity.ok(body);
    }

    /**
     * Detalhe de uma venda (pagamento) para popular o dialog de Visualização.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getVenda(@PathVariable Long id) {
        Pagamento p = pagamentoRepository.findById(id)
                .orElse(null);
        if (p == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", true,
                    "message", "Venda não encontrada"
            ));
        }

        Map<String, Object> dto = new HashMap<>();
        dto.put("id", p.getId());
        dto.put("criadoEm", p.getCriadoEm());
        dto.put("pagoEm", p.getPagoEm());
        dto.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        dto.put("metodo", p.getMetodo());

        // Mesa
        String mesaSlug = null;
        String mesaRotulo = null;
        try {
            if (p.getSessaoMesa() != null && p.getSessaoMesa().getMesa() != null) {
                mesaSlug = p.getSessaoMesa().getMesa().getSlug();
                mesaRotulo = p.getSessaoMesa().getMesa().getRotulo();
            }
        } catch (Exception ignored) {}
        dto.put("mesaSlug", mesaSlug);
        dto.put("mesaRotulo", mesaRotulo);

        // Beneficiário / Pagante
        dto.put("beneficiario", p.getSessaoConvidado() != null ? p.getSessaoConvidado().getNomeExibicao() : "Mesa toda");
        dto.put("beneficiarioId", p.getSessaoConvidado() != null ? p.getSessaoConvidado().getId() : null);
        dto.put("pagante", p.getPagante() != null ? p.getPagante().getNomeExibicao() : null);
        dto.put("paganteId", p.getPagante() != null ? p.getPagante().getId() : null);

        // Valores e referência
        dto.put("valor", p.getValor());
        dto.put("valorBase", p.getValorBase() != null ? p.getValorBase() : p.getValor());
        dto.put("valorTaxaServico", p.getValorTaxaServico());
        dto.put("percentualTaxaServico", p.getPercentualTaxaServico());
        dto.put("incluiTaxaServico", p.getIncluiTaxaServico());
        dto.put("providerRef", p.getProviderRef());

        // Tabs auxiliares
        dto.put("pagamentos", java.util.List.of(Map.of(
                "metodo", p.getMetodo(),
                "valor", p.getValor(),
                "pagoEm", p.getPagoEm(),
                "providerRef", p.getProviderRef()
        )));
        dto.put("itens", mapearItens(p));

        return ResponseEntity.ok(dto);
    }

    /**
     * Endpoint to get paginated sales records for the Sales Reports page.
     * Returns a clean DTO without dynamic form metadata.
     */
    @GetMapping("/report-table")
    public ResponseEntity<PaginatedSalesResponse> getSalesReportTable(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(defaultValue = "pagoEm") String ordenacao,
            @RequestParam(defaultValue = "desc") String direcao,
            @RequestParam(required = false) String filter) {

        // Use the listService to get paginated data with filtering
        Map<String, Object> serviceResponse = listService.list(pagina, tamanho, ordenacao, direcao, filter, messageResolver);

        // Safely cast and extract values
        List<Map<String, Object>> rawTableData = (List<Map<String, Object>>) serviceResponse.getOrDefault("table_data", java.util.Collections.emptyList());
        long totalElementos = ((Number) serviceResponse.getOrDefault("totalElementos", 0L)).longValue();
        int totalPaginas = ((Number) serviceResponse.getOrDefault("totalPaginas", 0)).intValue();
        int paginaAtual = ((Number) serviceResponse.getOrDefault("paginaAtual", 0)).intValue();
        int tamanhoPagina = ((Number) serviceResponse.getOrDefault("tamanhoPagina", 0)).intValue();

        List<SalesRecordDTO> salesRecords = rawTableData.stream()
                .map(row -> SalesRecordDTO.builder()
                        .id((Long) row.get("id"))
                        .pagoEm((LocalDateTime) row.get("pagoEm"))
                        .mesaSlug((String) row.get("mesaSlug"))
                        .mesaRotulo((String) row.get("mesaRotulo"))
                        .beneficiario((String) row.get("beneficiario"))
                        .pagante((String) row.get("pagante"))
                        .metodo((String) row.get("metodo"))
                        .valor((BigDecimal) row.get("valor"))
                        .valorBase((BigDecimal) row.get("valorBase"))
                        .valorTaxaServico((BigDecimal) row.get("valorTaxaServico"))
                        .providerRef((String) row.get("providerRef"))
                        .build())
                .collect(Collectors.toList());

        PaginatedSalesResponse response = PaginatedSalesResponse.builder()
                .table_data(salesRecords)
                .totalElementos(totalElementos)
                .totalPaginas(totalPaginas)
                .paginaAtual(paginaAtual)
                .tamanhoPagina(tamanhoPagina)
                .build();

        return ResponseEntity.ok(response);
    }

    private List<Map<String, Object>> mapearItens(Pagamento pagamento) {
        List<ItemPedido> itens = carregarItensParaPagamento(pagamento);
        if (itens == null || itens.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return itens.stream()
                .filter(item -> item.getStatus() != StatusItem.CANCELED)
                .map(item -> {
                    String descricao = montarDescricaoProduto(item);
                    java.math.BigDecimal valorUnitario = item.getPrecoUnitario();
                    java.math.BigDecimal valorTotal = valorUnitario != null
                            ? valorUnitario.multiply(java.math.BigDecimal.valueOf(item.getQuantidade()))
                            : java.math.BigDecimal.ZERO;

                    Map<String, Object> row = new HashMap<>();
                    row.put("id", item.getId());
                    row.put("descricaoProduto", descricao);
                    row.put("quantidade", item.getQuantidade());
                    row.put("valorUnitario", valorUnitario);
                    row.put("valorTotal", valorTotal);
                    return row;
                })
                .collect(Collectors.toList());
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

    private String montarDescricaoProduto(ItemPedido item) {
        Produto produto = item.getProduto();
        ProdutoSKU sku = item.getSku();
        String descricao = produto != null ? produto.getNome() : "Item";

        if (sku != null && sku.getVariacao() != null && !sku.getVariacao().isBlank()) {
            descricao = descricao + " (" + sku.getVariacao() + ")";
        }

        return descricao;
    }
}
