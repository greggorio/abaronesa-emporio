package com.baronesa.emporio.dynamicform.service;

import com.baronesa.emporio.dynamicform.entity.DynamicFormDefinition;
import com.baronesa.emporio.dynamicform.repository.DynamicFormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefinitionResetService {

    private final FormDefinitionLoaderService loaderService;
    private final DynamicFormRepository formRepository;

    /**
     * Reseta uma definição específica para o padrão original
     */
    @Transactional
    public Map<String, Object> resetToDefault(String entityType) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Com o novo sistema de migrations, não é possível resetar pois
        // todas as definições agora são mantidas no banco via migrations
        result.put("success", false);
        result.put("message", "Operação não suportada com o novo sistema de migrations");
        result.put("entityType", entityType);

        return result;
    }

    /**
     * Reseta todas as definições para os padrões originais
     */
    @Transactional
    public Map<String, Object> resetAll() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Com o novo sistema de migrations, não é possível resetar pois
        // todas as definições agora são mantidas no banco via migrations
        result.put("success", false);
        result.put("message", "Operação não suportada com o novo sistema de migrations");

        return result;
    }

    /**
     * Reseta apenas as definições que foram modificadas
     */
    @Transactional
    public Map<String, Object> resetModifiedOnly() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Com o novo sistema de migrations, não é possível resetar pois
        // todas as definições agora são mantidas no banco via migrations
        result.put("success", false);
        result.put("message", "Operação não suportada com o novo sistema de migrations");

        return result;
    }

    /**
     * Valida se uma definição pode ser resetada
     */
    public boolean canReset(String entityType) {
        // Com o novo sistema de migrations, não é possível resetar
        return false;
    }
}
