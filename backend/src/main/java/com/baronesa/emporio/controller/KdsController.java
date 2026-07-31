package com.baronesa.emporio.controller;

import com.baronesa.emporio.entity.ItemPedido;
import com.baronesa.emporio.entity.Pedido;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.enums.LocalPreparacao;
import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.enums.StatusPedido;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.util.ConfigManager;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import com.baronesa.emporio.events.SseEventsService;
import com.baronesa.emporio.service.NotificacaoService;
import com.baronesa.emporio.print.PrintWebSocketHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@PreAuthorize("hasAnyRole('KDS','ADMIN','SYSTEM','WAITER','CAIXA')")
@RequestMapping("/api/kds")
@RequiredArgsConstructor
public class KdsController {

    private final ItemPedidoRepository itemPedidoRepository;
    private final PedidoRepository pedidoRepository;
    private final SseEventsService eventsService;
    private final com.baronesa.emporio.service.PedidoService pedidoService;
    private final ConfigManager configManager;
    private final NotificacaoService notificacaoService;
    private final PrintWebSocketHandler printWebSocketHandler;

    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> queue() {
        String serviceMode = configManager.getConfig("site_service_mode", "waiter_delivery");
        List<ItemPedido> itens = itemPedidoRepository.findByStatus(StatusItem.QUEUED);
        itens.addAll(itemPedidoRepository.findByStatus(StatusItem.ACCEPTED));
        itens.addAll(itemPedidoRepository.findByStatus(StatusItem.PREPARING));
        itens.addAll(itemPedidoRepository.findByStatus(StatusItem.READY));

        List<Map<String, Object>> tickets = itens.stream().map(i -> {
            Pedido pedidoEnt = i.getPedido();
            // Item info
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            String itemNome = i.getProduto().getNome();
            String variacao = (i.getSku() != null && i.getSku().getVariacao() != null) ? i.getSku().getVariacao() : null;
            if (variacao != null && !variacao.isBlank()) {
                itemNome = itemNome + " (" + variacao + ")";
            }
            item.put("nome", itemNome);
            item.put("quantidade", i.getQuantidade());
            item.put("observacoes", i.getObservacoes());
            item.put("necessitaPreparacao", i.getProduto().getNecessitaPreparacao() != null ? i.getProduto().getNecessitaPreparacao() : true);
            if (i.getSku() != null) {
                item.put("skuId", i.getSku().getId());
                item.put("variacao", i.getSku().getVariacao());
            }

            // Mesa info
            java.util.Map<String, Object> mesa = new java.util.HashMap<>();
            mesa.put("slug", i.getPedido().getSessaoMesa().getMesa().getSlug());
            mesa.put("rotulo", i.getPedido().getSessaoMesa().getMesa().getRotulo());
            mesa.put("referencia", i.getPedido().getSessaoMesa().getMesa().getReferencia());

            // Pedido info
            java.util.Map<String, Object> pedido = new java.util.HashMap<>();
            pedido.put("criadoEm", pedidoEnt.getCriadoEm() != null ? pedidoEnt.getCriadoEm().toString() : null);
            pedido.put("itemCount", pedidoEnt.getItens() != null ? pedidoEnt.getItens().size() : 0);

            // Ticket
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("itemPedidoId", i.getId());
            m.put("pedidoId", pedidoEnt.getId());
            m.put("estacao", determinarEstacao(i.getProduto()));
            m.put("status", i.getStatus().name().toLowerCase());
            m.put("atualizadoEm", i.getAtualizadoEm() != null ? i.getAtualizadoEm().toString() : java.time.LocalDateTime.now().toString());
            m.put("item", item);
            m.put("mesa", mesa);
            m.put("pedido", pedido);
            m.put("serviceMode", serviceMode);
            return m;
        }).collect(Collectors.toList());

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("tickets", tickets);
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/tickets/{itemPedidoId}")
    public ResponseEntity<Map<String, Object>> atualizarStatus(@PathVariable Long itemPedidoId,
                                                               @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        String motivoCodigo = body != null ? body.get("motivoCodigo") : null;
        String motivoDetalhe = body != null ? body.get("motivoDetalhe") : null;
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }

        ItemPedido item = itemPedidoRepository.findById(itemPedidoId)
                .orElseThrow(() -> new NotFoundException("Item do pedido não encontrado"));

        StatusItem novo;
        try {
            novo = StatusItem.valueOf(status.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("status inválido");
        }

        // Usar PedidoService para centralizar a lógica e disparar baixa de estoque no ACCEPTED
        item = pedidoService.atualizarStatusItem(itemPedidoId, novo, motivoCodigo, motivoDetalhe);

        // Atualiza status do pedido de forma básica
        Pedido pedido = item.getPedido();
        if (novo == StatusItem.ACCEPTED && pedido.getStatus() == StatusPedido.PENDING) {
            pedido.setStatus(StatusPedido.ACCEPTED);
        } else if (novo == StatusItem.PREPARING) {
            pedido.setStatus(StatusPedido.PREPARING);
        } else if (novo == StatusItem.READY) {
            // se todos prontos
            boolean todosReady = pedido.getItens().stream().allMatch(i -> i.getStatus() == StatusItem.READY || i.getStatus() == StatusItem.DELIVERED);
            if (todosReady) pedido.setStatus(StatusPedido.READY);
        } else if (novo == StatusItem.DELIVERED) {
            boolean todosEntregues = pedido.getItens().stream().allMatch(i -> i.getStatus() == StatusItem.DELIVERED);
            if (todosEntregues) pedido.setStatus(StatusPedido.DELIVERED);
        }
        pedidoRepository.save(pedido);

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("itemPedidoId", item.getId());
        resp.put("status", item.getStatus().name().toLowerCase());

        try {
            String serviceMode = configManager.getConfig("site_service_mode", "waiter_delivery");
            Boolean necessitaPreparacao = item.getProduto() != null && item.getProduto().getNecessitaPreparacao() != null
                    ? item.getProduto().getNecessitaPreparacao()
                    : true;

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("itemPedidoId", item.getId());
            payload.put("pedidoId", pedido.getId());
            payload.put("status", item.getStatus().name().toLowerCase());
            payload.put("estacao", item.getEstacao());
            payload.put("serviceMode", serviceMode);
            payload.put("necessitaPreparacao", necessitaPreparacao);
            payload.put("pedidoItemCount", pedido.getItens() != null ? pedido.getItens().size() : 0);

            // Publicar para a sessão específica (clientes na mesa)
            eventsService.publish(pedido.getSessaoMesa().getId(), "kds.status_changed", payload);

            // Publicar para o canal global do KDS
            eventsService.publishKds("kds.status_changed", payload);

            // Criar notificação (cliente) se pickup
            if ("customer_pickup".equalsIgnoreCase(serviceMode)) {
                SessaoConvidado convidado = pedido.getSessaoConvidado();
                if (convidado != null && pedido.getSessaoMesa() != null) {
                    String titulo = "Pedido pronto";
                    String msg = "Pedido #" + pedido.getId() + " está pronto. Retire no balcão.";
                    try {
                        var notifPayload = java.util.Map.of(
                                "pedidoId", pedido.getId()
                        );
                        notificacaoService.criarNotificacao(
                                pedido.getSessaoMesa(),
                                convidado,
                                "kds_ready",
                                titulo,
                                msg,
                                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(notifPayload)
                        );
                    } catch (Exception e) {
                        // Evita quebrar fluxo de SSE por falha ao salvar notificação
                        // Apenas logamos em debug para não poluir o log
                    }
                }
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/tickets/{itemPedidoId}/print")
    public ResponseEntity<Map<String, Object>> imprimirTicket(@PathVariable Long itemPedidoId) {
        ItemPedido item = itemPedidoRepository.findById(itemPedidoId)
                .orElseThrow(() -> new NotFoundException("Item do pedido não encontrado"));

        String estacao = determinarEstacao(item.getProduto());
        String route = "bar".equalsIgnoreCase(estacao) ? "BAR" : "COZINHA";
        String tipo = "bar".equalsIgnoreCase(estacao) ? "KDS_BAR" : "KDS_COZINHA";
        String jobId = "kds-" + itemPedidoId + "-" + System.currentTimeMillis();

        // Monta payload esperado pelo Print Agent (ESC/POS)
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("pedido_numero", item.getPedido().getId());
        payload.put("mesa", item.getPedido().getSessaoMesa().getMesa().getRotulo());
        payload.put("garcom", null);
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        java.util.List<Map<String, Object>> itens = new ArrayList<>();
        java.util.Map<String, Object> linha = new java.util.HashMap<>();
        String nome = item.getProduto().getNome();
        if (item.getSku() != null && item.getSku().getVariacao() != null && !item.getSku().getVariacao().isBlank()) {
            nome = nome + " (" + item.getSku().getVariacao() + ")";
        }
        linha.put("qtd", item.getQuantidade());
        linha.put("nome", nome);
        if (item.getObservacoes() != null && !item.getObservacoes().isBlank()) {
            linha.put("observacao", item.getObservacoes());
        }
        itens.add(linha);
        payload.put("itens", itens);
        // Se existir observação do pedido, envie (campos atuais do Pedido não têm observação)
        // payload.put("observacao_geral", item.getPedido().getMotivoCancelamento());

        java.util.Map<String, Object> job = new java.util.HashMap<>();
        job.put("id", jobId);
        job.put("route", route);
        job.put("tipo", tipo);
        job.put("format", "ESC_POS");
        job.put("copies", 1);
        job.put("idempotency_key", jobId);
        job.put("payload", payload);

        try {
            printWebSocketHandler.sendPrintJob(job);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "message", "agent_not_connected"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "job_id", jobId
        ));
    }

    /**
     * Determina a estação baseado no localPreparacao do produto
     */
    private String determinarEstacao(Produto produto) {
        if (produto.getLocalPreparacao() == null) {
            return "kitchen"; // default
        }
        return produto.getLocalPreparacao() == LocalPreparacao.BAR ? "bar" : "kitchen";
    }
}
