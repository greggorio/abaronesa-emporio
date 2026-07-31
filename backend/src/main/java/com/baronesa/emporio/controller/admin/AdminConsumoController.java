package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.ConsumoRequestDTO;
import com.baronesa.emporio.service.ConsumoReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/consumo")
@RequiredArgsConstructor
public class AdminConsumoController {

    private final ConsumoReceiptService consumoReceiptService;

    @PostMapping("/comprovante")
    public ResponseEntity<byte[]> gerarComprovanteConsumo(@Valid @RequestBody ConsumoRequestDTO consumoRequest) throws Exception {
        byte[] pdfContent = consumoReceiptService.generateConsumoReceiptPdf(consumoRequest);

        String fileName = "comprovante_consumo_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + fileName)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfContent);
    }
}