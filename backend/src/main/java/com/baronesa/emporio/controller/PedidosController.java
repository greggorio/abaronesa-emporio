package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.CriarPedidoRequest;
import com.baronesa.emporio.dto.CriarPedidoResponse;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.enums.StatusPedido;
import com.baronesa.emporio.enums.LocalPreparacao;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.repository.*;
import com.baronesa.emporio.service.CardapioService;
import com.baronesa.emporio.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import com.baronesa.emporio.events.SseEventsService;
import org.springframework.data.domain.PageRequest;
import com.baronesa.emporio.dto.ProdutoFrequenteDTO;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import com.baronesa.emporio.util.ConfigManager;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidosController {

    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final SseEventsService eventsService;
    private final NotificacaoService notificacaoService;
    private final ProdutoSKURepository produtoSKURepository;
    private final com.baronesa.emporio.service.PedidoService pedidoService;
    private final CardapioService cardapioService;
    private final UsuarioRepository usuarioRepository;
    private final ConfigManager configManager;

    @GetMapping("/me/favoritos")
    public ResponseEntity<List<ProdutoFrequenteDTO>> meusFavoritos(@RequestParam(defaultValue = "5") int limit) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        
        return ResponseEntity.ok(itemPedidoRepository.findFavoritosByUsuarioId(
            usuario.getId(), 
            PageRequest.of(0, limit)
        ));
    }

    @PostMapping
    public ResponseEntity<CriarPedidoResponse> criarPedido(
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken,
            @RequestHeader(name = "X-Sessao-Mesa", required = false) Long sessaoMesaId,
            @RequestBody CriarPedidoRequest request
    ) {
        if (!StringUtils.hasText(guestToken)) {
            throw new IllegalArgumentException("Cabeçalho X-Guest-Token é obrigatório");
        }

        SessaoConvidado convidado = sessaoConvidadoRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new NotFoundException("Convidado não encontrado"));

        if (sessaoMesaId != null && !convidado.getSessaoMesa().getId().equals(sessaoMesaId)) {
            throw new IllegalArgumentException("Sessão de mesa não corresponde ao convidado");
        }

        if (convidado.getSessaoMesa().getStatus() != null && convidado.getSessaoMesa().getStatus() == com.baronesa.emporio.enums.StatusSessao.CLOSED) {
            throw new IllegalArgumentException("A sessão da mesa está encerrada e não aceita novos pedidos");
        }

        if (request == null || request.itens() == null || request.itens().isEmpty()) {
            throw new IllegalArgumentException("Itens do pedido são obrigatórios");
        }

        Pedido pedido = Pedido.builder()
                .sessaoMesa(convidado.getSessaoMesa())
                .sessaoConvidado(convidado)
                .status(StatusPedido.PENDING)
                .origem("pwa")
                .build();

        pedido = pedidoRepository.save(pedido);

        for (CriarPedidoRequest.Item it : request.itens()) {
            Produto produto;
            com.baronesa.emporio.entity.ProdutoSKU sku = null;
            BigDecimal preco;

            if (it.skuId() != null) {
                // Buscar SKU e usar o preço do SKU (fallback produto)
                sku = produtoSKURepository.findById(it.skuId())
                        .orElseThrow(() -> new NotFoundException("SKU não encontrado: " + it.skuId()));
                produto = sku.getProduto();
                preco = cardapioService.calcularPrecoAtualParaPedido(produto, sku, guestToken);
            } else {
                produto = produtoRepository.findById(it.produtoId())
                        .orElseThrow(() -> new NotFoundException("Produto não encontrado: " + it.produtoId()));
                preco = cardapioService.calcularPrecoAtualParaPedido(produto, null, guestToken);
            }

            Integer qtd = (it.quantidade() != null && it.quantidade() > 0) ? it.quantidade() : 1;

            ItemPedido item = ItemPedido.builder()
                    .pedido(pedido)
                    .produto(produto)
                    .sku(sku)
                    .quantidade(qtd)
                    .precoUnitario(preco)
                    .observacoes(it.observacoes())
                    .status(StatusItem.QUEUED)
                    .estacao(resolveEstacao(produto, sku))
                    .build();
            itemPedidoRepository.save(item);
        }

        List<ItemPedido> itens = itemPedidoRepository.findByPedido(pedido);

        // Emit event order.created
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("pedidoId", pedido.getId());
            payload.put("status", pedido.getStatus() != null ? pedido.getStatus().name().toLowerCase() : "pending");
            payload.put("sessaoConvidadoId", convidado.getId());
            payload.put("nomeConvidado", convidado.getNomeExibicao() != null ? convidado.getNomeExibicao() : "Convidado");
            payload.put("isHost", convidado.getHost() != null ? convidado.getHost() : false);
            payload.put("itens", itens.stream()
                    .filter(ip -> ip.getProduto() != null)
                    .map(ip -> java.util.Map.of(
                        "itemPedidoId", ip.getId(),
                        "produtoId", ip.getProduto().getId(),
                        "produtoNome", ip.getProduto().getNome() != null ? ip.getProduto().getNome() : "",
                        "quantidade", ip.getQuantidade(),
                        "status", ip.getStatus() != null ? ip.getStatus().name().toLowerCase() : "queued"
                    )).toList());

            if (convidado.getSessaoMesa() != null && convidado.getSessaoMesa().getId() != null) {
                eventsService.publish(convidado.getSessaoMesa().getId(), "order.created", payload);

                // Salvar notificação no banco para o host (se o pedido não for do próprio host)
                if (!Boolean.TRUE.equals(convidado.getHost())) {
                    // Buscar o host da sessão
                    SessaoConvidado host = sessaoConvidadoRepository.findBySessaoMesa_Id(convidado.getSessaoMesa().getId())
                            .stream()
                            .filter(c -> Boolean.TRUE.equals(c.getHost()))
                            .findFirst()
                            .orElse(null);

                    if (host != null) {
                        int totalItens = itens.stream().mapToInt(ItemPedido::getQuantidade).sum();
                        String primeiroProduto = itens.isEmpty() ? "item" : itens.get(0).getProduto().getNome();
                        String descricao = itens.size() == 1
                                ? itens.get(0).getQuantidade() + "x " + primeiroProduto
                                : totalItens + " itens (" + primeiroProduto + (itens.size() > 1 ? " +" + (itens.size() - 1) : "") + ")";

                        String titulo = "🍽️ Novo pedido de " + (convidado.getNomeExibicao() != null ? convidado.getNomeExibicao() : "Convidado");
                        String payloadJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);

                        notificacaoService.criarNotificacao(convidado.getSessaoMesa(), host, "order_created", titulo, descricao, payloadJson);
                    }
                }
            }

            // Publicar cada item individualmente para o KDS
            for (ItemPedido ip : itens) {
                if (ip.getProduto() == null || pedido.getSessaoMesa() == null || pedido.getSessaoMesa().getMesa() == null) {
                    continue;
                }

                java.util.Map<String, Object> kdsPayload = new java.util.HashMap<>();
                kdsPayload.put("itemPedidoId", ip.getId());
                kdsPayload.put("pedidoId", pedido.getId());
                kdsPayload.put("estacao", ip.getEstacao());
                kdsPayload.put("status", ip.getStatus() != null ? ip.getStatus().name().toLowerCase() : "queued");

                // Item info
                java.util.Map<String, Object> itemInfo = new java.util.HashMap<>();
                String itemNome = ip.getProduto().getNome() != null ? ip.getProduto().getNome() : "";
                String variacao = (ip.getSku() != null && ip.getSku().getVariacao() != null) ? ip.getSku().getVariacao() : null;
                if (variacao != null && !variacao.isBlank()) {
                    itemNome = itemNome + " (" + variacao + ")";
                }
                itemInfo.put("nome", itemNome);
                itemInfo.put("quantidade", ip.getQuantidade());
                itemInfo.put("observacoes", ip.getObservacoes());
                itemInfo.put("necessitaPreparacao", ip.getProduto().getNecessitaPreparacao() != null ? ip.getProduto().getNecessitaPreparacao() : true);
                if (ip.getSku() != null) {
                    itemInfo.put("skuId", ip.getSku().getId());
                    itemInfo.put("variacao", ip.getSku().getVariacao());
                }
                kdsPayload.put("item", itemInfo);

                // Mesa info
                java.util.Map<String, Object> mesaInfo = new java.util.HashMap<>();
                mesaInfo.put("slug", pedido.getSessaoMesa().getMesa().getSlug());
                mesaInfo.put("rotulo", pedido.getSessaoMesa().getMesa().getRotulo());
                mesaInfo.put("referencia", pedido.getSessaoMesa().getMesa().getReferencia());
                kdsPayload.put("mesa", mesaInfo);

                // Pedido info
                java.util.Map<String, Object> pedidoInfo = new java.util.HashMap<>();
                pedidoInfo.put("criadoEm", pedido.getCriadoEm() != null ? pedido.getCriadoEm().toString() : null);
                pedidoInfo.put("itemCount", pedido.getItens() != null ? pedido.getItens().size() : 0);
                kdsPayload.put("pedido", pedidoInfo);

                kdsPayload.put("atualizadoEm", ip.getAtualizadoEm() != null ? ip.getAtualizadoEm().toString() : java.time.LocalDateTime.now().toString());
                kdsPayload.put("serviceMode", configManager.getConfig("site_service_mode", "waiter_delivery"));

                eventsService.publishKds("kds.new_item", kdsPayload);
            }
        } catch (Exception e) {
            System.err.println("Erro ao publicar eventos de pedido: " + e.getMessage());
            e.printStackTrace();
        }
        CriarPedidoResponse resp = new CriarPedidoResponse(
                pedido.getId(),
                pedido.getStatus().name().toLowerCase(),
                itens.stream().map(ip -> new CriarPedidoResponse.Item(
                        ip.getId(),
                        ip.getProduto().getId(),
                        ip.getQuantidade(),
                        ip.getStatus().name().toLowerCase()
                )).collect(Collectors.toList())
        );

        return ResponseEntity.ok(resp);
    }

    private String resolveEstacao(Produto produto, com.baronesa.emporio.entity.ProdutoSKU sku) {
        LocalPreparacao escolhido = produto != null ? produto.getLocalPreparacao() : null;
        if (escolhido == null) {
            return "kitchen";
        }
        return escolhido == LocalPreparacao.BAR ? "bar" : "kitchen";
    }

    @GetMapping("/{pedidoId}")
    public ResponseEntity<CriarPedidoResponse> obterPedido(@PathVariable Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado"));
        List<ItemPedido> itens = itemPedidoRepository.findByPedido(pedido);
        CriarPedidoResponse resp = new CriarPedidoResponse(
                pedido.getId(),
                pedido.getStatus().name().toLowerCase(),
                itens.stream().map(ip -> new CriarPedidoResponse.Item(
                        ip.getId(),
                        ip.getProduto().getId(),
                        ip.getQuantidade(),
                        ip.getStatus().name().toLowerCase()
                )).collect(Collectors.toList())
        );
        return ResponseEntity.ok(resp);
    }

    /**
     * Endpoint para atualizar o status de um item do pedido.
     * Quando o status muda para ACCEPTED, faz a baixa automática dos insumos.
     */
    @PatchMapping("/itens/{itemPedidoId}/status")
    public ResponseEntity<?> atualizarStatusItem(
            @PathVariable Long itemPedidoId,
            @RequestBody java.util.Map<String, String> request
    ) {
        try {
            String statusStr = request.get("status");
            if (statusStr == null || statusStr.isBlank()) {
                throw new IllegalArgumentException("Campo 'status' é obrigatório");
            }

            StatusItem novoStatus = StatusItem.valueOf(statusStr.toUpperCase());
            String motivoCodigo = request.get("motivoCodigo");
            String motivoDetalhe = request.get("motivoDetalhe");
            ItemPedido item = pedidoService.atualizarStatusItem(itemPedidoId, novoStatus, motivoCodigo, motivoDetalhe);

            return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "Status atualizado com sucesso",
                    "itemPedidoId", item.getId(),
                    "status", item.getStatus().name().toLowerCase()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Status inválido: " + e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of(
                    "success", false,
                    "message", "Erro ao atualizar status: " + e.getMessage()
            ));
        }
    }
}
