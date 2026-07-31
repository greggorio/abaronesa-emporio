package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.NotificacaoResponse;
import com.baronesa.emporio.entity.Notificacao;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.service.NotificacaoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
public class NotificacoesController {

    private final NotificacaoService notificacaoService;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final MessageResolver messageResolver;
    private final ObjectMapper objectMapper;

    @GetMapping("/nao-lidas")
    public ResponseEntity<List<NotificacaoResponse>> buscarNaoLidas(
            @RequestHeader(name = "X-Guest-Token", required = true) String guestToken,
            HttpServletRequest request) {

        SessaoConvidado convidado = sessaoConvidadoRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new NotFoundException("Convidado não encontrado"));

        List<Notificacao> notificacoes = notificacaoService.buscarNaoLidas(convidado);
        Locale locale = resolveLocale(request);

        List<NotificacaoResponse> response = notificacoes.stream()
                .map(n -> {
                    Map<String, Object> payload = parsePayload(n.getPayloadJson());
                    String titulo = resolveTitle(n, payload, locale);
                    String mensagem = resolveMessage(n, payload, locale);
                    return new NotificacaoResponse(
                            n.getId(),
                            n.getTipo(),
                            titulo,
                            mensagem,
                            n.getLida(),
                            n.getCriadoEm(),
                            n.getLidaEm(),
                            n.getPayloadJson()
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/contador")
    public ResponseEntity<java.util.Map<String, Long>> contarNaoLidas(
            @RequestHeader(name = "X-Guest-Token", required = true) String guestToken) {

        SessaoConvidado convidado = sessaoConvidadoRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new NotFoundException("Convidado não encontrado"));

        long count = notificacaoService.contarNaoLidas(convidado);

        return ResponseEntity.ok(java.util.Map.of("count", count));
    }

    @PatchMapping("/{notificacaoId}/marcar-lida")
    public ResponseEntity<Void> marcarComoLida(
            @PathVariable Long notificacaoId,
            @RequestHeader(name = "X-Guest-Token", required = true) String guestToken) {

        // Validar que a notificação pertence ao convidado
        SessaoConvidado convidado = sessaoConvidadoRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new NotFoundException("Convidado não encontrado"));

        notificacaoService.marcarComoLida(notificacaoId);

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/marcar-todas-lidas")
    public ResponseEntity<Void> marcarTodasComoLidas(
            @RequestHeader(name = "X-Guest-Token", required = true) String guestToken) {

        SessaoConvidado convidado = sessaoConvidadoRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new NotFoundException("Convidado não encontrado"));

        notificacaoService.marcarTodasComoLidas(convidado);

        return ResponseEntity.ok().build();
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String resolveTitle(Notificacao notificacao, Map<String, Object> payload, Locale locale) {
        String tipo = notificacao.getTipo();
        if ("guest_joined".equals(tipo)) {
            return messageResolver.getMessage("notifications.guestJoined.title", locale);
        }
        if ("order_created".equals(tipo)) {
            String nome = getString(payload, "nomeConvidado", "Convidado");
            return messageResolver.getMessage("notifications.orderCreated.title", new Object[]{nome}, locale);
        }
        if ("kds_ready".equals(tipo)) {
            Object pedidoId = payload.get("pedidoId");
            return messageResolver.getMessage("notifications.kdsReady.title", new Object[]{pedidoId}, locale);
        }
        return notificacao.getTitulo();
    }

    private String resolveMessage(Notificacao notificacao, Map<String, Object> payload, Locale locale) {
        String tipo = notificacao.getTipo();
        if ("guest_joined".equals(tipo)) {
            String nome = getString(payload, "nomeExibicao", "Convidado");
            return messageResolver.getMessage("notifications.guestJoined.message", new Object[]{nome}, locale);
        }
        if ("order_created".equals(tipo)) {
            return buildOrderDescription(payload, locale);
        }
        if ("kds_ready".equals(tipo)) {
            Object pedidoId = payload.get("pedidoId");
            if (pedidoId != null) {
                return messageResolver.getMessage("notifications.kdsReady.message", new Object[]{pedidoId}, locale);
            }
        }
        return notificacao.getMensagem();
    }

    private String buildOrderDescription(Map<String, Object> payload, Locale locale) {
        Object itensObj = payload.get("itens");
        if (!(itensObj instanceof List<?> itens) || itens.isEmpty()) {
            return messageResolver.getMessage("notifications.orderCreated.itemsFallback", locale);
        }
        int totalItems = 0;
        String primeiroProduto = null;
        for (Object itemObj : itens) {
            if (!(itemObj instanceof Map<?, ?> item)) continue;
            Object quantidadeObj = item.get("quantidade");
            int quantidade = quantidadeObj instanceof Number ? ((Number) quantidadeObj).intValue() : 0;
            totalItems += Math.max(quantidade, 0);
            if (primeiroProduto == null) {
                Object nomeObj = item.get("produtoNome");
                if (nomeObj != null) {
                    primeiroProduto = String.valueOf(nomeObj);
                }
            }
        }
        if (primeiroProduto == null || primeiroProduto.isBlank()) {
            primeiroProduto = messageResolver.getMessage("notifications.orderCreated.itemFallback", locale);
        }
        if (itens.size() == 1) {
            int qtd = Math.max(totalItems, 1);
            return messageResolver.getMessage("notifications.orderCreated.singleItem",
                    new Object[]{qtd, primeiroProduto}, locale);
        }
        int extraCount = Math.max(itens.size() - 1, 0);
        return messageResolver.getMessage("notifications.orderCreated.multipleItems",
                new Object[]{Math.max(totalItems, itens.size()), primeiroProduto, extraCount}, locale);
    }

    private String getString(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private Locale resolveLocale(HttpServletRequest request) {
        String langParam = request.getParameter("lang");
        if (langParam != null && !langParam.isBlank()) {
            Locale fromParam = Locale.forLanguageTag(langParam.trim());
            if (!fromParam.getLanguage().isBlank()) {
                return fromParam;
            }
        }
        String header = request.getHeader("Accept-Language");
        if (header != null && !header.isBlank()) {
            String first = header.split(",")[0].trim();
            Locale fromHeader = Locale.forLanguageTag(first);
            if (!fromHeader.getLanguage().isBlank()) {
                return fromHeader;
            }
        }
        return new Locale("pt", "BR");
    }
}
