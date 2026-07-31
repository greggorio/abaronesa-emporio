package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppController {

    private final WhatsAppService whatsAppService;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            return ResponseEntity.ok(whatsAppService.status());
        } catch (Exception e) {
            log.error("Erro ao verificar status WhatsApp", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "erro", "Erro ao verificar status"
            ));
        }
    }

    @GetMapping("/qr")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> qr() {
        String png = whatsAppService.fetchQrPng();
        if (png == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "QR indisponível"));
        }
        return ResponseEntity.ok(Map.of("success", true, "png", png));
    }

    @PostMapping("/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> start() {
        boolean ok = whatsAppService.startIfNeeded();
        return ResponseEntity.ok(Map.of("success", ok));
    }

    @PostMapping("/disconnect")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> disconnect() {
        boolean ok = whatsAppService.disconnect();
        return ResponseEntity.ok(Map.of("success", ok));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> me() {
        var info = whatsAppService.me();
        return ResponseEntity.ok(info);
    }

    /**
     * Envio de PDF de teste para validar a integração com o microserviço.
     * Gera um PDF simples em memória (string em UTF-8).
     */
    @PostMapping("/enviar-teste")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> enviarTeste(@RequestBody Map<String, String> payload) {
        try {
            String telefone = payload != null ? payload.get("telefone") : null;
            if (telefone == null || telefone.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Telefone obrigatório"));
            }

            byte[] pdf = gerarPdfTesteBasico();
            whatsAppService.enviarPdf(telefone, pdf, "teste.pdf", "Teste de envio").join();
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Erro ao enviar teste WA", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/enviar-arquivo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> enviarArquivo(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("telefone") String telefone,
            @RequestParam(value = "mensagem", required = false) String mensagem
    ) {
        try {
            if (telefone == null || telefone.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Telefone obrigatório"));
            }
            if (arquivo == null || arquivo.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Arquivo obrigatório"));
            }

            String nomeArquivo = arquivo.getOriginalFilename();
            if (nomeArquivo == null || nomeArquivo.isBlank()) {
                nomeArquivo = "arquivo.pdf";
            }
            
            byte[] bytes = arquivo.getBytes();
            String caption = mensagem != null ? mensagem : "Segue arquivo em anexo";

            whatsAppService.enviarPdf(telefone, bytes, nomeArquivo, caption).join();
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Arquivo enviado com sucesso"
            ));

        } catch (Exception e) {
            log.error("Erro ao enviar arquivo via WhatsApp", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, 
                    "message", "Erro ao enviar: " + e.getMessage()
            ));
        }
    }

    private byte[] gerarPdfTesteBasico() {
        // PDF super simples (header + texto) em bytes. Pode ser substituído por HtmlConverter se preferir.
        try {
            // Minimal PDF structure (not pretty, but valid)
            String content = "BT /F1 12 Tf 72 712 Td (Teste de envio WhatsApp) Tj ET";
            String pdf = "%PDF-1.1\n" +
                    "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n" +
                    "2 0 obj << /Type /Pages /Count 1 /Kids [3 0 R] >> endobj\n" +
                    "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n" +
                    "4 0 obj << /Length " + content.length() + " >> stream\n" + content + "\nendstream endobj\n" +
                    "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n" +
                    "xref\n0 6\n0000000000 65535 f \n" +
                    "0000000010 00000 n \n0000000079 00000 n \n0000000175 00000 n \n0000000405 00000 n \n0000000520 00000 n \n" +
                    "trailer << /Root 1 0 R /Size 6 >>\nstartxref\n630\n%%EOF";
            return pdf.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao gerar PDF de teste: " + e.getMessage());
        }
    }
}
