package com.baronesa.emporio.dynamicform.controller;

import com.baronesa.emporio.dynamicform.entity.DynamicFormDefinition;
import com.baronesa.emporio.dynamicform.service.FormDefinitionLoaderService;
import com.baronesa.emporio.dynamicform.service.FormDefinitionResetService;
import com.baronesa.emporio.dynamicform.service.FormDefinitionStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller para gerenciamento de definições de formulários dinâmicos.
 * Com o novo sistema de migrations, as definições são mantidas via migrations.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/form-definitions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM')")
public class FormDefinitionManagementController {

    private final FormDefinitionStatusService statusService;
    private final FormDefinitionResetService resetService;
    private final FormDefinitionLoaderService loaderService;

    /**
     * Lista todas as definições com seus status
     */
    @GetMapping("/status")
    public ResponseEntity<List<Map<String, Object>>> listAllWithStatus() {
        log.info("📋 Listando status de todas as definições de formulário");
        List<Map<String, Object>> result = statusService.listAllWithStatus();
        return ResponseEntity.ok(result);
    }

    /**
     * Obtém o status de uma definição específica
     */
    @GetMapping("/status/{entityType}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String entityType) {
        log.info("🔍 Verificando status de: {}", entityType);
        String status = statusService.getStatus(entityType);
        return ResponseEntity.ok(Map.of(
                "entityType", entityType,
                "status", status
        ));
    }

    /**
     * Compara uma definição atual com a original
     */
    @GetMapping("/compare/{entityType}")
    public ResponseEntity<Map<String, Object>> compare(@PathVariable String entityType) {
        log.info("🔄 Comparando definição de: {}", entityType);
        Map<String, Object> comparison = statusService.compareWithOriginal(entityType);
        return ResponseEntity.ok(comparison);
    }

    /**
     * Verifica se existem definições modificadas
     */
    @GetMapping("/has-modifications")
    public ResponseEntity<Map<String, Object>> hasModifications() {
        boolean hasModified = statusService.hasModifiedDefinitions();
        long count = statusService.countModifiedDefinitions();

        return ResponseEntity.ok(Map.of(
                "hasModifications", hasModified,
                "modifiedCount", count
        ));
    }
}
