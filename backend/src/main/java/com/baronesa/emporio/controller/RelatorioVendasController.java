package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.relatorios.RelatorioVendasFiltroDTO;
import com.baronesa.emporio.service.RelatorioVendasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
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
import java.time.temporal.ChronoUnit;

@Slf4j
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioVendasController {

    private static final int MAX_DIAS = 90;

    private final RelatorioVendasService relatorioVendasService;

    @GetMapping("/vendas/pdf")
    public ResponseEntity<byte[]> gerarRelatorioVendasPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        validarParametros(dataInicio, dataFim);

        log.info("Gerando relatório de vendas - dataInicio={}, dataFim={}", dataInicio, dataFim);

        RelatorioVendasFiltroDTO filtros = new RelatorioVendasFiltroDTO(dataInicio, dataFim);
        byte[] pdfBytes = relatorioVendasService.gerarRelatorioVendasPdf(filtros);

        String nomeArquivo = String.format("vendas_%s_%s.pdf",
                dataInicio.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                dataFim.format(DateTimeFormatter.ofPattern("yyyyMMdd")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.builder("attachment")
                .filename(nomeArquivo)
                .build());
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    private void validarParametros(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null || dataFim == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataInicio e dataFim são obrigatórios");
        }

        if (dataInicio.isAfter(dataFim)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataInicio não pode ser posterior a dataFim");
        }

        if (dataFim.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataFim não pode ser uma data futura");
        }

        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim);
        if (dias > MAX_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período máximo permitido é de 90 dias");
        }
    }
}
