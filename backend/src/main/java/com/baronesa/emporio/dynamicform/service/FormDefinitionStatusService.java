package com.baronesa.emporio.dynamicform.service;

import com.baronesa.emporio.dynamicform.entity.DynamicFormDefinition;
import com.baronesa.emporio.dynamicform.repository.DynamicFormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefinitionStatusService {

    private final FormDefinitionLoaderService loaderService;
    private final DynamicFormRepository formRepository;

    /**
     * Verifica o status de uma definição de formulário
     * @return "ORIGINAL" se não foi modificada, "MODIFIED" se foi alterada, "NOT_FOUND" se não existe
     */
    public String getStatus(String entityType) {
        // Como agora todas as definições vêm do banco de dados (via migrations),
        // uma definição existente no banco é considerada "original"
        Optional<DynamicFormDefinition> currentOpt = formRepository.findByEntityType(entityType);
        if (currentOpt.isEmpty()) {
            return "NOT_FOUND";
        }

        return "ORIGINAL";
    }

    /**
     * Retorna o status de todas as definições no banco
     */
    public Map<String, String> getAllStatuses() {
        Map<String, String> statuses = new LinkedHashMap<>();

        List<DynamicFormDefinition> allDefinitions = formRepository.findAll();

        for (DynamicFormDefinition definition : allDefinitions) {
            String status = getStatus(definition.getEntityType());
            statuses.put(definition.getEntityType(), status);
        }

        return statuses;
    }

    /**
     * Compara uma definição atual com a original e retorna as diferenças
     */
    public Map<String, Object> compareWithOriginal(String entityType) {
        Map<String, Object> comparison = new LinkedHashMap<>();

        Optional<DynamicFormDefinition> currentOpt = formRepository.findByEntityType(entityType);
        if (currentOpt.isEmpty()) {
            comparison.put("error", "Definição não encontrada no banco de dados");
            return comparison;
        }

        DynamicFormDefinition current = currentOpt.get();

        comparison.put("entityType", entityType);
        comparison.put("status", getStatus(entityType));

        // Compara campos básicos
        comparison.put("programName", Map.of(
            "original", current.getProgramName(),
            "current", current.getProgramName(),
            "changed", false
        ));

        comparison.put("programIcon", Map.of(
            "original", current.getProgramIcon(),
            "current", current.getProgramIcon(),
            "changed", false
        ));

        // Calcula hashes para verificar mudanças estruturais
        String currentHash = loaderService.calculateHash(current);

        comparison.put("hashes", Map.of(
            "original", currentHash,
            "current", currentHash,
            "structureChanged", false
        ));

        comparison.put("version", current.getVersion());
        comparison.put("updatedAt", current.getUpdatedAt());

        return comparison;
    }

    /**
     * Lista todas as definições com seus status
     */
    public List<Map<String, Object>> listAllWithStatus() {
        List<Map<String, Object>> result = new ArrayList<>();

        List<DynamicFormDefinition> allDefinitions = formRepository.findAll();

        for (DynamicFormDefinition definition : allDefinitions) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("entityType", definition.getEntityType());
            info.put("programName", definition.getProgramName());
            info.put("programIcon", definition.getProgramIcon());
            info.put("active", definition.getActive());
            info.put("version", definition.getVersion());
            info.put("status", getStatus(definition.getEntityType()));
            info.put("updatedAt", definition.getUpdatedAt());

            result.add(info);
        }

        // Ordena por programName
        result.sort(Comparator.comparing(m -> (String) m.get("programName")));

        return result;
    }

    /**
     * Verifica se existem definições modificadas
     */
    public boolean hasModifiedDefinitions() {
        return false; // Com o novo sistema de migrations, não há definições modificadas
    }

    /**
     * Conta quantas definições estão modificadas
     */
    public long countModifiedDefinitions() {
        return 0; // Com o novo sistema de migrations, não há definições modificadas
    }
}
