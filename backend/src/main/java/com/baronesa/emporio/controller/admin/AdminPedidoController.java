package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.CriarPedidoAdminRequest;
import com.baronesa.emporio.dto.CriarPedidoRequest;
import com.baronesa.emporio.dto.CriarPedidoResponse;
import com.baronesa.emporio.entity.ItemPedido;
import com.baronesa.emporio.entity.Pedido;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.enums.StatusPedido;
import com.baronesa.emporio.enums.StatusSessao;
import com.baronesa.emporio.events.SseEventsService;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.repository.PedidoRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import com.baronesa.emporio.repository.ProdutoSKURepository;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.repository.SessaoMesaRepository;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.service.CardapioService;
import com.baronesa.emporio.service.NotificacaoService;
import com.baronesa.emporio.service.SessaoMesaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/mesas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM','WAITER','CAIXA')")
public class AdminPedidoController {

    private final SessaoMesaRepository sessaoMesaRepository;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository produtoSKURepository;
    private final CardapioService cardapioService;
    private final SseEventsService eventsService;
    private final NotificacaoService notificacaoService;
    private final SessaoMesaService sessaoMesaService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/sessoes/{sessaoMesaId}/convidados")
    public ResponseEntity<?> listarConvidados(@PathVariable Long sessaoMesaId) {
        SessaoMesa sessaoMesa = sessaoMesaRepository.findById(sessaoMesaId).orElse(null);
        if (sessaoMesa == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")
            ));
        }
        List<SessaoConvidado> convidados = sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoMesaId);
        Long hostId = convidados.stream()
                .filter(c -> Boolean.TRUE.equals(c.getHost()))
                .map(SessaoConvidado::getId)
                .findFirst()
                .orElse(null);

        List<Map<String, Object>> data = convidados.stream()
                .map(c -> Map.<String, Object>of(
                        "sessaoConvidadoId", c.getId(),
                        "nome", c.getNomeExibicao(),
                        "host", c.getHost()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "convidados", data,
                "hostId", hostId,
                "sessaoStatus", sessaoMesa.getStatus()
        ));
    }

    @PostMapping("/sessoes/{sessaoMesaId}/pedidos")
    public ResponseEntity<?> criarPedidoStaff(@PathVariable Long sessaoMesaId,
                                              @RequestBody CriarPedidoAdminRequest request) {
        if (request == null || request.itens() == null || request.itens().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "itens_obrigatorios", "message", "Itens do pedido são obrigatórios")
            ));
        }

        SessaoMesa sessaoMesa = sessaoMesaRepository.findById(sessaoMesaId).orElse(null);
        if (sessaoMesa == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")
            ));
        }
        if (sessaoMesa.getStatus() != StatusSessao.OPEN) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", Map.of("code", "sessao_encerrada", "message", "Sessão encerrada não aceita novos pedidos")
            ));
        }

        SessaoConvidado convidado = resolverConvidado(sessaoMesaId, request.sessaoConvidadoId());
        if (convidado == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "convidado_invalido", "message", "Convidado não encontrado para a sessão")
            ));
        }

        Pedido pedido = Pedido.builder()
                .sessaoMesa(sessaoMesa)
                .sessaoConvidado(convidado)
                .status(StatusPedido.PENDING)
                .origem(request.origem() != null ? request.origem() : "staff")
                .build();
        pedido = pedidoRepository.save(pedido);

        for (CriarPedidoRequest.Item it : request.itens()) {
            Produto produto;
            ProdutoSKU sku = null;
            if (it.skuId() != null) {
                sku = produtoSKURepository.findById(it.skuId())
                        .orElse(null);
                if (sku == null) {
                    return ResponseEntity.status(404).body(Map.of(
                            "error", Map.of("code", "sku_nao_encontrado", "message", "SKU não encontrado: " + it.skuId())
                    ));
                }
                produto = sku.getProduto();
            } else if (it.produtoId() != null) {
                produto = produtoRepository.findById(it.produtoId())
                        .orElse(null);
                if (produto == null) {
                    return ResponseEntity.status(404).body(Map.of(
                            "error", Map.of("code", "produto_nao_encontrado", "message", "Produto não encontrado: " + it.produtoId())
                    ));
                }
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", Map.of("code", "produto_obrigatorio", "message", "Informe produtoId ou skuId")
                ));
            }

            Integer qtd = (it.quantidade() != null && it.quantidade() > 0) ? it.quantidade() : 1;
            BigDecimal preco = cardapioService.calcularPrecoAtualParaPedido(produto, sku);

            ItemPedido item = ItemPedido.builder()
                    .pedido(pedido)
                    .produto(produto)
                    .sku(sku)
                    .quantidade(qtd)
                    .precoUnitario(preco)
                    .observacoes(trimOrNull(it.observacoes()))
                    .status(StatusItem.QUEUED)
                    .build();
            itemPedidoRepository.save(item);
        }

        List<ItemPedido> itens = itemPedidoRepository.findByPedido(pedido);
        publicarEventosPedido(pedido, convidado, itens);

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

    @PostMapping("/balcao/pedidos")
    public ResponseEntity<?> criarPedidoBalcao(@RequestBody CriarPedidoAdminRequest request,
                                               @RequestParam(defaultValue = "BALCAO") String mesaSlug) {
        if (request == null || request.itens() == null || request.itens().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "itens_obrigatorios", "message", "Itens do pedido são obrigatórios")
            ));
        }
        Usuario comprador = null;
        if (request.compradorId() != null) {
            comprador = usuarioRepository.findById(request.compradorId()).orElse(null);
            if (comprador == null || comprador.getRoles() == null || comprador.getRoles().contains(Usuario.Role.SYSTEM)
                    || Boolean.FALSE.equals(comprador.getAtivo())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", Map.of("code", "comprador_invalido", "message", "Usuário comprador inválido")
                ));
            }
        }
        // Reutiliza mesa BALCAO e convidado host
        SessaoConvidado convidado = sessaoMesaService.obterOuCriarSessaoEBalcaoGuest(
                mesaSlug,
                comprador != null ? comprador.getNome() : "Balcão",
                comprador
        );
        SessaoMesa sessaoMesa = convidado.getSessaoMesa();
        if (sessaoMesa == null || sessaoMesa.getStatus() != StatusSessao.OPEN) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", Map.of("code", "sessao_encerrada", "message", "Sessão encerrada não aceita novos pedidos")
            ));
        }

        CriarPedidoAdminRequest payload = new CriarPedidoAdminRequest(
                convidado.getId(),
                request.itens(),
                request.origem() != null ? request.origem() : "fast_checkout",
                request.compradorId()
        );
        return criarPedidoStaff(sessaoMesa.getId(), payload);
    }

    private SessaoConvidado resolverConvidado(Long sessaoMesaId, Long sessaoConvidadoId) {
        if (sessaoConvidadoId != null) {
            SessaoConvidado convidado = sessaoConvidadoRepository.findById(sessaoConvidadoId).orElse(null);
            if (convidado != null && convidado.getSessaoMesa() != null && sessaoMesaId.equals(convidado.getSessaoMesa().getId())) {
                return convidado;
            }
            return null;
        }
        return sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoMesaId).stream()
                .filter(c -> Boolean.TRUE.equals(c.getHost()))
                .findFirst()
                .orElseGet(() -> sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoMesaId).stream().findFirst().orElse(null));
    }

    private String trimOrNull(String val) {
        if (!StringUtils.hasText(val)) return null;
        String trimmed = val.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void publicarEventosPedido(Pedido pedido, SessaoConvidado convidado, List<ItemPedido> itens) {
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

            if (pedido.getSessaoMesa() != null && pedido.getSessaoMesa().getId() != null) {
                eventsService.publish(pedido.getSessaoMesa().getId(), "order.created", payload);

                if (!Boolean.TRUE.equals(convidado.getHost())) {
                    SessaoConvidado host = sessaoConvidadoRepository.findBySessaoMesa_Id(pedido.getSessaoMesa().getId())
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

                        notificacaoService.criarNotificacao(pedido.getSessaoMesa(), host, "order_created", titulo, descricao, payloadJson);
                    }
                }
            }

            for (ItemPedido ip : itens) {
                if (ip.getProduto() == null || pedido.getSessaoMesa() == null || pedido.getSessaoMesa().getMesa() == null) {
                    continue;
                }

                java.util.Map<String, Object> kdsPayload = new java.util.HashMap<>();
                kdsPayload.put("itemPedidoId", ip.getId());
                kdsPayload.put("pedidoId", pedido.getId());
                kdsPayload.put("estacao", ip.getEstacao());
                kdsPayload.put("status", ip.getStatus() != null ? ip.getStatus().name().toLowerCase() : "queued");

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

                java.util.Map<String, Object> mesaInfo = new java.util.HashMap<>();
                mesaInfo.put("slug", pedido.getSessaoMesa().getMesa().getSlug());
                mesaInfo.put("rotulo", pedido.getSessaoMesa().getMesa().getRotulo());
                mesaInfo.put("referencia", pedido.getSessaoMesa().getMesa().getReferencia());
                kdsPayload.put("mesa", mesaInfo);

                java.util.Map<String, Object> pedidoInfo = new java.util.HashMap<>();
                pedidoInfo.put("criadoEm", pedido.getCriadoEm() != null ? pedido.getCriadoEm().toString() : null);
                kdsPayload.put("pedido", pedidoInfo);

                kdsPayload.put("atualizadoEm", ip.getAtualizadoEm() != null ? ip.getAtualizadoEm().toString() : java.time.LocalDateTime.now().toString());

                eventsService.publishKds("kds.new_item", kdsPayload);
            }
        } catch (Exception e) {
            System.err.println("Erro ao publicar eventos de pedido (admin): " + e.getMessage());
            e.printStackTrace();
        }
    }
}
