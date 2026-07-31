package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.relatorios.MovimentoCaixaFiltroDTO;
import com.baronesa.emporio.service.RelatorioMovimentoCaixaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioMovimentoCaixaController {

    private final RelatorioMovimentoCaixaService relatorioMovimentoCaixaService;

    /**
     * Gera o PDF do relatório de movimento de caixa.
     * Se não for informada data, considera a data atual.
     */
    @GetMapping("/movimento-caixa/pdf")
    public ResponseEntity<byte[]> gerarRelatorioMovimentoCaixaPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        try {
            if (data == null) {
                data = LocalDate.now();
            }

            log.info("Gerando relatório de movimento de caixa - Data: {}", data);

            if (data.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Data não pode ser futura");
            }

            MovimentoCaixaFiltroDTO filtros = new MovimentoCaixaFiltroDTO(data);
            byte[] pdfBytes = relatorioMovimentoCaixaService.gerarRelatorioMovimentoCaixaPdf(filtros);

            String nomeArquivo = String.format("movimento_caixa_%s.pdf",
                    data.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", nomeArquivo);
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.setExpires(0);

            log.info("Relatório gerado com sucesso - Tamanho: {} bytes", pdfBytes.length);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Parâmetros inválidos para relatório de movimento de caixa: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao gerar relatório de movimento de caixa", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erro ao gerar relatório: " + e.getMessage());
        }
    }
}
