package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.importacao.AmostraErro;
import com.baronesa.emporio.dto.importacao.CategoriaDetectada;
import com.baronesa.emporio.dto.importacao.ConfirmacaoImportacaoResponse;
import com.baronesa.emporio.dto.importacao.ImportPreviewResponse;
import com.baronesa.emporio.service.ImportacaoProdutoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
@Slf4j
public class ImportacaoProdutoController {

    private final ImportacaoProdutoService importacaoProdutoService;

    @PostMapping("/import/preview")
    public ResponseEntity<ImportPreviewResponse> gerarPreviewImportacao(
            @RequestParam("file") MultipartFile file) throws IOException {
        
        log.info("Recebendo requisição para preview de importação de produtos");
        
        ImportPreviewResponse response = importacaoProdutoService.gerarPreview(file);
        
        log.info("Preview gerado com sucesso. Total: {}, Válidos: {}, Inválidos: {}, Duplicados: {}",
                response.getTotal(), response.getValidos(), response.getInvalidos(), response.getDuplicadosInternos());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/import/confirm")
    public ResponseEntity<ConfirmacaoImportacaoResponse> confirmarImportacao(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Recebendo requisição para confirmação de importação de produtos");

        ConfirmacaoImportacaoResponse response = importacaoProdutoService.confirmarImportacao(file);

        log.info("Confirmação de importação concluída. Total: {}, Processados: {}, Criados: {}, Erros: {}",
                response.getTotal(), response.getProcessados(), response.getCriadas(), response.getErros());

        return ResponseEntity.ok(response);
    }
}