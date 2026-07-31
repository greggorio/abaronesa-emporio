package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ChamadoResponse;
import com.baronesa.emporio.dto.CriarChamadoRequest;
import com.baronesa.emporio.entity.Chamado;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.enums.StatusChamado;
import com.baronesa.emporio.enums.TipoChamado;
import com.baronesa.emporio.events.SseEventsService;
import com.baronesa.emporio.repository.ChamadoRepository;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.repository.SessaoMesaRepository;
import com.baronesa.emporio.service.LanguageDetectionService;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chamados")
@RequiredArgsConstructor
@Slf4j
public class ChamadosController {

    private final ChamadoRepository chamadoRepository;
    private final SessaoMesaRepository sessaoMesaRepository;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final SseEventsService eventsService;
    private final LanguageDetectionService languageDetectionService;
    private final ConfigManager configManager;

    @PostMapping
    public ResponseEntity<?> criarChamado(
            @RequestBody CriarChamadoRequest request,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken) {

        if (request.sessaoMesaId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "sessao_mesa_obrigatoria", "message", "sessaoMesaId é obrigatório")
            ));
        }

        SessaoMesa sessaoMesa = sessaoMesaRepository.findById(request.sessaoMesaId()).orElse(null);
        if (sessaoMesa == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")
            ));
        }

        // Buscar convidado se token fornecido
        SessaoConvidado convidado = null;
        if (guestToken != null && !guestToken.isBlank()) {
            convidado = sessaoConvidadoRepository.findByGuestToken(guestToken).orElse(null);
        }

        // Validar tipo
        TipoChamado tipo;
        try {
            tipo = TipoChamado.valueOf(request.tipo().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "tipo_invalido", "message", "Tipo deve ser: garcom, conta ou ajuda")
            ));
        }

        // Processar observação para detecção e tradução de idioma
        String processedObservacao = request.observacao();
        if (processedObservacao != null && !processedObservacao.trim().isEmpty()) {
            try {
                if (languageDetectionService.isDifferentFromMainLanguage(processedObservacao)) {
                    processedObservacao = languageDetectionService.translateToMainLanguage(processedObservacao);

                    // Registrar log da tradução
                    String mainLanguage = configManager.getConfig("erp_language", "pt_BR");
                    log.info("Tradução de observação de chamado: idioma original detectado e traduzido para {}", mainLanguage);
                }
            } catch (Exception e) {
                log.warn("Falha ao detectar/traduzir idioma da observação: {}", e.getMessage());
                // Continuar com a observação original em caso de falha
            }
        }

        // Criar chamado com a observação processada
        Chamado chamado = Chamado.builder()
                .sessaoMesa(sessaoMesa)
                .sessaoConvidado(convidado)
                .tipo(tipo)
                .status(StatusChamado.PENDENTE)
                .observacao(processedObservacao)
                .build();

        chamado = chamadoRepository.save(chamado);

        // Publicar evento SSE
        try {
            String mesaSlug = sessaoMesa.getMesa() != null ? sessaoMesa.getMesa().getSlug() : "";
            String mesaRotulo = sessaoMesa.getMesa() != null ? sessaoMesa.getMesa().getRotulo() : "";
            String mesaReferencia = sessaoMesa.getMesa() != null && sessaoMesa.getMesa().getReferencia() != null
                    ? sessaoMesa.getMesa().getReferencia()
                    : "";
            String observacao = chamado.getObservacao() != null ? chamado.getObservacao() : "";

            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("chamadoId", chamado.getId());
            payload.put("sessaoMesaId", sessaoMesa.getId());
            payload.put("mesaSlug", mesaSlug);
            payload.put("mesaRotulo", mesaRotulo);
            payload.put("mesaReferencia", mesaReferencia);
            payload.put("tipo", chamado.getTipo().name().toLowerCase());
            payload.put("observacao", observacao);
            payload.put("criadoEm", chamado.getCriadoEm().toString());

            eventsService.publishKds("chamado.novo", payload);
        } catch (Exception e) {
            System.err.println("Erro ao publicar chamado.novo: " + e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "chamadoId", chamado.getId()
        ));
    }

    @GetMapping("/pendentes")
    @PreAuthorize("hasAnyRole('WAITER','CAIXA','ADMIN','SYSTEM')")
    public ResponseEntity<List<ChamadoResponse>> listarPendentes() {
        List<Chamado> chamados = chamadoRepository.findByStatusOrderByCriadoEmAsc(StatusChamado.PENDENTE);
        List<ChamadoResponse> response = chamados.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/contador")
    @PreAuthorize("hasAnyRole('WAITER','CAIXA','ADMIN','SYSTEM')")
    public ResponseEntity<Map<String, Long>> contarPendentes() {
        long count = chamadoRepository.countByStatus(StatusChamado.PENDENTE);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{chamadoId}/atender")
    @PreAuthorize("hasAnyRole('WAITER','CAIXA','ADMIN','SYSTEM')")
    public ResponseEntity<?> atenderChamado(
            @PathVariable Long chamadoId,
            @RequestParam(required = false) String atendidoPor) {

        Chamado chamado = chamadoRepository.findById(chamadoId).orElse(null);
        if (chamado == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Chamado não encontrado")
            ));
        }

        chamado.setStatus(StatusChamado.ATENDIDO);
        chamado.setAtendidoEm(LocalDateTime.now());
        chamado.setAtendidoPor(atendidoPor);
        chamadoRepository.save(chamado);

        // Publicar evento SSE
        try {
            Map<String, Object> payload = Map.of(
                    "chamadoId", chamado.getId(),
                    "status", "atendido"
            );
            eventsService.publishKds("chamado.atendido", payload);
        } catch (Exception e) {
            System.err.println("Erro ao publicar chamado.atendido: " + e.getMessage());
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/{chamadoId}/liberar-pagamento")
    @PreAuthorize("hasAnyRole('WAITER','CAIXA','ADMIN','SYSTEM')")
    public ResponseEntity<?> liberarPagamento(
            @PathVariable Long chamadoId,
            @RequestParam(required = false) String atendidoPor) {

        Chamado chamado = chamadoRepository.findById(chamadoId).orElse(null);
        if (chamado == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Chamado não encontrado")
            ));
        }

        SessaoMesa sessaoMesa = chamado.getSessaoMesa();
        if (sessaoMesa == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")
            ));
        }

        chamado.setStatus(StatusChamado.ATENDIDO);
        chamado.setAtendidoEm(LocalDateTime.now());
        chamado.setAtendidoPor(atendidoPor);
        chamadoRepository.save(chamado);

        sessaoMesa.setSelfCheckoutLiberado(true);
        sessaoMesa.setSelfCheckoutLiberadoEm(LocalDateTime.now());
        sessaoMesaRepository.save(sessaoMesa);

        try {
            Map<String, Object> payload = Map.of(
                    "chamadoId", chamado.getId(),
                    "status", "atendido"
            );
            eventsService.publishKds("chamado.atendido", payload);
        } catch (Exception e) {
            System.err.println("Erro ao publicar chamado.atendido: " + e.getMessage());
        }

        try {
            Map<String, Object> payload = Map.of(
                    "sessaoMesaId", sessaoMesa.getId(),
                    "liberadoEm", sessaoMesa.getSelfCheckoutLiberadoEm().toString()
            );
            eventsService.publish(sessaoMesa.getId(), "checkout.released", payload);
        } catch (Exception e) {
            System.err.println("Erro ao publicar checkout.released: " + e.getMessage());
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    private ChamadoResponse toResponse(Chamado chamado) {
        Long tempoEspera = null;
        if (chamado.getStatus() == StatusChamado.PENDENTE) {
            tempoEspera = Duration.between(chamado.getCriadoEm(), LocalDateTime.now()).getSeconds();
        }

        String mesaReferencia = chamado.getSessaoMesa().getMesa() != null ? chamado.getSessaoMesa().getMesa().getReferencia() : null;
        return new ChamadoResponse(
                chamado.getId(),
                chamado.getSessaoMesa().getId(),
                chamado.getSessaoMesa().getMesa() != null ? chamado.getSessaoMesa().getMesa().getSlug() : "",
                chamado.getSessaoMesa().getMesa() != null ? chamado.getSessaoMesa().getMesa().getRotulo() : "",
                mesaReferencia,
                chamado.getTipo().name().toLowerCase(),
                chamado.getStatus().name().toLowerCase(),
                chamado.getObservacao(),
                chamado.getCriadoEm(),
                chamado.getAtendidoPor(),
                chamado.getAtendidoEm(),
                tempoEspera
        );
    }
}
