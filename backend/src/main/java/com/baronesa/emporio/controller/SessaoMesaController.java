package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.CriarConvidadoRequest;
import com.baronesa.emporio.dto.CriarConvidadoResponse;
import com.baronesa.emporio.dto.ContaMesaResponse;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.repository.SessaoMesaRepository;
import com.baronesa.emporio.service.NotificacaoService;
import com.baronesa.emporio.service.SessaoMesaService;
import com.baronesa.emporio.events.SseEventsService;
import com.baronesa.emporio.service.ContaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mesas")
@RequiredArgsConstructor
public class SessaoMesaController {

    private final SessaoMesaService sessaoMesaService;
    private final SseEventsService eventsService;
    private final ContaService contaService;
    private final NotificacaoService notificacaoService;
    private final SessaoMesaRepository sessaoMesaRepository;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final com.baronesa.emporio.repository.UsuarioRepository usuarioRepository;

    @GetMapping("/me/ativa")
    public ResponseEntity<?> sessaoAtivaUsuario() {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        com.baronesa.emporio.entity.Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null) return ResponseEntity.ok(java.util.Map.of("ativa", false));

        var convidadoOpt = sessaoConvidadoRepository.findFirstByUsuario_IdAndSessaoMesa_Status(usuario.getId(), com.baronesa.emporio.enums.StatusSessao.OPEN);
        
        if (convidadoOpt.isPresent()) {
            SessaoMesa sessao = convidadoOpt.get().getSessaoMesa();
            ContaMesaResponse conta = contaService.contaMesa(sessao.getId());
            return ResponseEntity.ok(java.util.Map.of(
                "ativa", true,
                "mesaSlug", sessao.getMesa().getSlug(),
                "mesaRotulo", sessao.getMesa().getRotulo(),
                "totalConsumido", conta.subtotalCentavos()
            ));
        }
        
        return ResponseEntity.ok(java.util.Map.of("ativa", false));
    }

    @GetMapping("/{mesaSlug}/sessao")
    public ResponseEntity<?> verificarSessao(@PathVariable String mesaSlug) {
        SessaoMesa sessaoAtiva = sessaoMesaService.getSessaoAtivaPorMesaSlug(mesaSlug);

        if (sessaoAtiva == null) {
            return ResponseEntity.ok(java.util.Map.of(
                    "sessaoAtiva", false
            ));
        }

        // Contar convidados na sessão
        var convidados = sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoAtiva.getId());
        long totalConvidados = convidados.size();
        boolean assistida = sessaoAtiva.getObservacoes() != null
                && sessaoAtiva.getObservacoes().toLowerCase(java.util.Locale.ROOT).contains("assistida");

        return ResponseEntity.ok(java.util.Map.of(
                "sessaoAtiva", true,
                "sessaoMesaId", sessaoAtiva.getId(),
                "totalConvidados", totalConvidados,
                "mesaRotulo", sessaoAtiva.getMesa() != null ? sessaoAtiva.getMesa().getRotulo() : mesaSlug,
                "abertaEm", sessaoAtiva.getAbertaEm().toString(),
                "assistida", assistida
        ));
    }

    @PostMapping("/{mesaSlug}/convidados")
    public ResponseEntity<CriarConvidadoResponse> criarConvidado(@PathVariable String mesaSlug,
                                                                 @RequestBody(required = false) CriarConvidadoRequest request) {
        CriarConvidadoResponse resp = sessaoMesaService.criarConvidado(mesaSlug, request);

        // Publicar evento SSE para notificar outros convidados (especialmente o host)
        try {
            String nomeExibicao = (request != null && request.nomeExibicao() != null && !request.nomeExibicao().isBlank())
                    ? request.nomeExibicao().trim()
                    : "Convidado";

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("sessaoConvidadoId", resp.sessaoConvidadoId());
            payload.put("sessaoMesaId", resp.sessaoMesaId());
            payload.put("nomeExibicao", nomeExibicao);
            payload.put("isHost", resp.host() != null ? resp.host() : false);
            payload.put("timestamp", java.time.LocalDateTime.now().toString());

            eventsService.publish(resp.sessaoMesaId(), "guest.joined", payload);

            // Salvar notificação no banco para o host (se não for o próprio host que está entrando)
            if (Boolean.FALSE.equals(resp.host())) {
                SessaoMesa sessaoMesa = sessaoMesaRepository.findById(resp.sessaoMesaId()).orElse(null);
                if (sessaoMesa != null) {
                    // Buscar o host da sessão
                    SessaoConvidado host = sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoMesa.getId())
                            .stream()
                            .filter(c -> Boolean.TRUE.equals(c.getHost()))
                            .findFirst()
                            .orElse(null);

                    if (host != null) {
                        String titulo = "👋 Novo convidado";
                        String mensagem = nomeExibicao + " entrou na mesa";
                        String payloadJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);

                        notificacaoService.criarNotificacao(sessaoMesa, host, "guest_joined", titulo, mensagem, payloadJson);
                    }
                }
            }
        } catch (Exception e) {
            // Não falhar a requisição se SSE der problema
            System.err.println("Erro ao publicar guest.joined: " + e.getMessage());
        }

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/sessoes/{sessaoMesaId}/fechar")
    public ResponseEntity<?> fecharSessaoMesa(@PathVariable Long sessaoMesaId,
                                              @RequestHeader(name = "X-Guest-Token", required = false) String guestToken) {
        // Permitir fechar apenas pelo host da mesa (ou staff em evoluções futuras)
        if (guestToken == null || guestToken.isBlank()) {
            return ResponseEntity.status(403).body(java.util.Map.of("error", java.util.Map.of("code", "forbidden", "message", "Apenas o responsável pela mesa pode fechar")));
        }
        var convidadoOpt = sessaoMesaService.getSessaoConvidadoByToken(guestToken);
        if (convidadoOpt.isEmpty() || !Boolean.TRUE.equals(convidadoOpt.get().getHost()) || !convidadoOpt.get().getSessaoMesa().getId().equals(sessaoMesaId)) {
            return ResponseEntity.status(403).body(java.util.Map.of("error", java.util.Map.of("code", "forbidden", "message", "Apenas o responsável pela mesa pode fechar")));
        }
        ContaMesaResponse conta = contaService.contaMesa(sessaoMesaId);
        if (conta.devidoCentavos() != 0) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", java.util.Map.of("code", "mesa_em_aberto", "message", "Existem valores em aberto para a mesa")
            ));
        }
        sessaoMesaService.fecharSessao(sessaoMesaId);
        try {
            var payload = java.util.Map.of(
                    "status", "closed",
                    "sessaoMesaId", sessaoMesaId,
                    "fechadaEm", java.time.LocalDateTime.now().toString()
            );
            eventsService.publish(sessaoMesaId, "table.closed", payload);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(java.util.Map.of("success", true));
    }
}
