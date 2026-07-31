package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.RelatorioVendasProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM', 'CAIXA')")
public class RelatorioVendasProdutoController {

    private static final int MAX_DIAS = 90;
    private final RelatorioVendasProdutoService relatorioService;

    @GetMapping("/vendas-produtos/pdf")
    public ResponseEntity<byte[]> getRelatorioPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) Long produtoId,
            @RequestParam(defaultValue = "false") boolean detalhado) {
        
        validarParametros(dataInicio, dataFim);
        byte[] pdf = relatorioService.gerarRelatorioPdf(dataInicio, dataFim, produtoId, detalhado);

        String filename = detalhado ? "vendas-produtos-analitico.pdf" : "vendas-produtos-sintetico.pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
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
