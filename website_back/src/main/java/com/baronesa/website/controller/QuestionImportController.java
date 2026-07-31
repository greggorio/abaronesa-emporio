package com.baronesa.website.controller;

import com.baronesa.website.dto.questions.QuestionImportPreviewDTO;
import com.baronesa.website.dto.questions.QuestionImportRequestDTO;
import com.baronesa.website.dto.questions.QuestionImportResultDTO;
import com.baronesa.website.dto.questions.ImportSummaryDTO;
import com.baronesa.website.service.questions.importer.QuestionImportPreviewService;
import com.baronesa.website.service.questions.importer.QuestionImportService;
import com.baronesa.website.service.questions.importer.QuestionImportRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/questions/import")
@RequiredArgsConstructor
@Tag(name = "Question Import", description = "API para importação de perguntas em massa")
@SecurityRequirement(name = "bearerAuth")
public class QuestionImportController {

    private final QuestionImportPreviewService questionImportPreviewService;
    private final QuestionImportService questionImportService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Pré-visualização de importação de perguntas (dry-run)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<QuestionImportPreviewDTO> previewImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "request", required = false) String requestJson) throws Exception {

        // Validar se o arquivo foi fornecido
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createPreviewErrorResponse());
        }

        QuestionImportRequestDTO requestDto = prepareRequest(requestJson, true, 20);

        // Extrair informações do arquivo
        String filename = file.getOriginalFilename();
        String contentType = resolveContentType(filename, file.getContentType());

        // Processar a pré-visualização
        try {
            QuestionImportPreviewDTO result = questionImportPreviewService.previewImport(
                    filename,
                    contentType,
                    file.getInputStream(),
                    requestDto
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erro durante a pré-visualização de importação: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(createPreviewErrorResponse());
        }
    }

    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importação definitiva de perguntas",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<QuestionImportResultDTO> commitImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "request", required = false) String requestJson) throws Exception {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createCommitErrorResponse());
        }

        QuestionImportRequestDTO requestDto = prepareRequest(requestJson, false, 20);

        String filename = file.getOriginalFilename();
        String contentType = resolveContentType(filename, file.getContentType());

        log.info("=== COMMIT IMPORT ===");
        log.info("Filename: {}, ContentType: {}, FileSize: {} bytes", filename, contentType, file.getSize());
        log.info("Request: {}", requestJson);
        log.info("RequestDTO: dedupeMode={}, activeMode={}, transactionMode={}, dryRun={}, previewLimit={}",
                requestDto.getDedupeMode(), requestDto.getActiveMode(), requestDto.getTransactionMode(),
                requestDto.getDryRun(), requestDto.getPreviewLimit());

        try {
            QuestionImportResultDTO result = questionImportService.importQuestions(
                    filename,
                    contentType,
                    file.getInputStream(),
                    requestDto
            );
            log.info("Import result - TotalReceived: {}, TotalParsed: {}, Imported: {}, Updated: {}, Skipped: {}, Errors: {}",
                    result.getSummary().getTotalReceived(), result.getSummary().getTotalParsed(),
                    result.getSummary().getImportedCount(), result.getSummary().getUpdatedCount(),
                    result.getSummary().getSkippedCount(), result.getSummary().getErrorCount());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Erro de validação durante o commit da importação: {}", e.getMessage(), e);
            log.error("Stack trace:", e);
            return ResponseEntity.badRequest()
                    .body(createCommitErrorResponse());
        } catch (Exception e) {
            log.error("Erro durante o commit da importação: {}", e.getMessage(), e);
            log.error("Stack trace:", e);
            return ResponseEntity.badRequest()
                    .body(createCommitErrorResponse());
        }
    }

    @GetMapping(value = "/template.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ByteArrayResource> downloadJsonTemplate() throws JsonProcessingException {
        List<QuestionImportRow> templateRows = List.of(new QuestionImportRow(
                1,
                "Pergunta de exemplo",
                List.of("Opção A", "Opção B"),
                0,
                10,
                true,
                1L,
                "https://example.com/image.png"
        ));

        byte[] payload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(templateRows);
        ByteArrayResource resource = new ByteArrayResource(payload);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=questions_import_template.json")
                .body(resource);
    }

    @GetMapping(value = "/template.csv", produces = "text/csv")
    public ResponseEntity<ByteArrayResource> downloadCsvTemplate() {
        String csv = "question,options,correctAnswer,points,active,categoryId,imageUrl\n" +
                "\"Pergunta de exemplo\",\"[\"\"Opção A\"\",\"\"Opção B\"\"]\",0,10,true,1,\"https://example.com/image.png\"\n";
        ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=questions_import_template.csv")
                .body(resource);
    }

    private QuestionImportPreviewDTO createPreviewErrorResponse() {
        // Criar um objeto de resposta de erro para preview
        return new QuestionImportPreviewDTO(
                null,                           // detectedFormat
                0,                              // totalParsed
                Collections.emptyList(),        // previewItems
                createEmptySummaryWithError(),  // summary
                null                            // importId
        );
    }

    private com.baronesa.website.dto.questions.ImportSummaryDTO createEmptySummaryWithError() {
        com.baronesa.website.dto.questions.ImportSummaryDTO summary = new com.baronesa.website.dto.questions.ImportSummaryDTO();
        summary.setTotalReceived(0);
        summary.setTotalParsed(0);
        summary.setImportedCount(0);
        summary.setUpdatedCount(0);
        summary.setSkippedCount(0);
        summary.setErrorCount(1);
        return summary;
    }

    private QuestionImportRequestDTO parseRequest(String requestJson) throws JsonProcessingException {
        if (StringUtils.hasText(requestJson)) {
            return objectMapper.readValue(requestJson, QuestionImportRequestDTO.class);
        }
        return new QuestionImportRequestDTO();
    }

    private QuestionImportRequestDTO prepareRequest(String requestJson, boolean dryRun, int previewLimit) throws JsonProcessingException {
        QuestionImportRequestDTO requestDto = parseRequest(requestJson);
        requestDto.setDryRun(dryRun);
        if (requestDto.getPreviewLimit() == null || requestDto.getPreviewLimit() <= 0) {
            requestDto.setPreviewLimit(previewLimit);
        }
        return requestDto;
    }

    private String resolveContentType(String filename, String contentType) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            return "text/csv";
        }
        if (filename != null && filename.toLowerCase().endsWith(".json")) {
            return "application/json";
        }
        return "application/octet-stream";
    }

    private QuestionImportResultDTO createCommitErrorResponse() {
        QuestionImportResultDTO result = new QuestionImportResultDTO();
        ImportSummaryDTO summary = new ImportSummaryDTO();
        summary.setTotalReceived(0);
        summary.setTotalParsed(0);
        summary.setImportedCount(0);
        summary.setUpdatedCount(0);
        summary.setSkippedCount(0);
        summary.setErrorCount(1);
        result.setSummary(summary);
        result.setItems(Collections.emptyList());
        result.setImportId(null);
        return result;
    }
}
