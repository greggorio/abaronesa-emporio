package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.entity.NfeModel;
import com.baronesa.emporio.nfe.dto.DanfeModel;
import com.baronesa.emporio.nfe.service.DanfePdfGeneratorService;
import com.baronesa.emporio.nfe.service.NfeParserService;
import com.baronesa.emporio.repository.NfeRepository;
import com.baronesa.emporio.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Endpoints administrativos para DANFE (NFe modelo 55).
 */
@RestController
@RequestMapping("/api/admin/nfe")
@RequiredArgsConstructor
@Slf4j
public class AdminNfeController {

    private final NfeRepository nfeRepository;
    private final NfeParserService nfeParserService;
    private final DanfePdfGeneratorService danfePdfGeneratorService;
    private final EmailService emailService;

    @GetMapping("/{id}/preview")
    public ResponseEntity<DanfeModel> preview(@PathVariable Long id) throws Exception {
        NfeModel nfe = nfeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("NFe não encontrada: " + id));
        DanfeModel danfe = nfeParserService.parseNfeXml(nfe);
        return ResponseEntity.ok(danfe);
    }

    @GetMapping("/{id}/danfe.pdf")
    public ResponseEntity<byte[]> gerarDanfe(@PathVariable Long id) throws Exception {
        NfeModel nfe = nfeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("NFe não encontrada: " + id));
        DanfeModel danfe = nfeParserService.parseNfeXml(nfe);
        byte[] pdf = danfePdfGeneratorService.generateDanfePdf(danfe);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=danfe-" + id + ".pdf")
                .body(pdf);
    }

    /**
     * Envia o DANFE por email gerando o PDF e anexando-o.
     */
    @PostMapping("/pagamentos/{pagamentoId}/email")
    public ResponseEntity<Map<String, Object>> enviarDanfePorEmail(
            @PathVariable Long pagamentoId,
            @RequestBody Map<String, String> payload
    ) throws Exception {
        String email = payload != null ? payload.get("email") : null;
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Email é obrigatório"));
        }

        Optional<NfeModel> nfeOpt = nfeRepository.findByIdVenda(pagamentoId);
        if (nfeOpt.isEmpty() || nfeOpt.get().isNFCe()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "NFe não encontrada para este pagamento"));
        }

        NfeModel nfe = nfeOpt.get();
        DanfeModel danfe = nfeParserService.parseNfeXml(nfe);
        byte[] pdfBytes = danfePdfGeneratorService.generateDanfePdf(danfe);

        String assunto = "Seu comprovante fiscal";
        String mensagem = "Segue em anexo o DANFE da sua compra.";
        String fileName = String.format("danfe_%s.pdf", nfe.getNumero() != null ? nfe.getNumero() : nfe.getId());

        emailService.sendPdf(email, assunto, mensagem, pdfBytes, fileName);

        return ResponseEntity.ok(Map.of("success", true));
    }
}
