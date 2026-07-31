package com.baronesa.emporio.dynamicform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.dynamicform.entity.DynamicFormDefinition;
import com.baronesa.emporio.dynamicform.repository.DynamicFormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefinitionLoaderService {

    private final DynamicFormRepository formRepository;
    private final ObjectMapper objectMapper;

    /**
     * Salva ou atualiza uma definição no banco de dados
     */
    @Transactional
    public DynamicFormDefinition saveOrUpdate(DynamicFormDefinition definition) {
        Optional<DynamicFormDefinition> existing =
            formRepository.findByEntityType(definition.getEntityType());

        if (existing.isPresent()) {
            DynamicFormDefinition existingDef = existing.get();
            existingDef.setProgramName(definition.getProgramName());
            existingDef.setProgramIcon(definition.getProgramIcon());
            existingDef.setComplexity(definition.getComplexity());
            existingDef.setTableOrder(definition.getTableOrder());
            existingDef.setActive(definition.getActive());
            existingDef.setFormStructure(definition.getFormStructure());
            existingDef.setTableColumns(definition.getTableColumns());
            existingDef.setCustomSlots(definition.getCustomSlots());
            existingDef.setUpdatedAt(LocalDateTime.now());
            existingDef.setVersion(existingDef.getVersion() + 1);

            return formRepository.save(existingDef);
        } else {
            definition.setCreatedAt(LocalDateTime.now());
            definition.setUpdatedAt(LocalDateTime.now());
            definition.setVersion(1L);
            return formRepository.save(definition);
        }
    }

    /**
     * Calcula o hash MD5 de uma definição para detectar alterações
     */
    public String calculateHash(DynamicFormDefinition definition) {
        try {
            // Serializa apenas os campos relevantes para comparação
            Map<String, Object> relevantData = new LinkedHashMap<>();
            relevantData.put("formStructure", definition.getFormStructure());
            relevantData.put("tableColumns", definition.getTableColumns());
            relevantData.put("customSlots", definition.getCustomSlots());

            String json = objectMapper.writeValueAsString(relevantData);

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(json.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            log.error("Erro ao calcular hash: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verifica se existe alguma definição no banco de dados
     */
    public boolean hasDefinitions() {
        return formRepository.count() > 0;
    }
}
