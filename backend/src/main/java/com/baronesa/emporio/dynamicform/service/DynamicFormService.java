package com.baronesa.emporio.dynamicform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.cache.FormConfigCache;
import com.baronesa.emporio.dynamicform.config.DynamicFormConfig;
import com.baronesa.emporio.dynamicform.dto.FormDefinitionDTO;
import com.baronesa.emporio.dynamicform.entity.DynamicFormDefinition;
import com.baronesa.emporio.dynamicform.entity.FormComplexityLevel;
import com.baronesa.emporio.dynamicform.exception.FormDefinitionNotFoundException;
import com.baronesa.emporio.dynamicform.registry.EntityRegistryService;
import com.baronesa.emporio.dynamicform.repository.DynamicFormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DynamicFormService {

    private final DynamicFormRepository repository;
    private final FormConfigCache cache;
    private final ObjectMapper objectMapper;
    private final EntityRegistryService entityRegistryService;

    /**
     * Busca uma definição de formulário ativa pelo tipo de entidade
     */
    public Optional<DynamicFormDefinition> findByEntityType(String entityType) {
        return repository.findByEntityTypeAndActiveTrue(entityType);
    }

    private void cleanNullValues(Map<String, Object> map) {
        map.entrySet().removeIf(entry -> entry.getValue() == null);

        map.forEach((key, value) -> {
            if (value instanceof Map) {
                cleanNullValues((Map<String, Object>) value);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                list.forEach(item -> {
                    if (item instanceof Map) {
                        cleanNullValues((Map<String, Object>) item);
                    }
                });
            }
        });
    }

    /**
     * Cria ou atualiza uma definição de formulário
     */
    public DynamicFormDefinition save(FormDefinitionDTO dto) {
        log.info("Salvando definição de formulário para entidade: {}", dto.getEntityType());

        normalizeReadOnly(dto);

        DynamicFormDefinition entity = repository.findByEntityType(dto.getEntityType())
                .orElse(new DynamicFormDefinition());

        // Mapear DTO para entidade
        entity.setEntityType(dto.getEntityType());
        entity.setProgramName(dto.getProgramName());
        entity.setProgramIcon(dto.getProgramIcon());
        entity.setTableOrder(dto.getTableOrder() != null ? dto.getTableOrder() : "id");
        entity.setComplexity(dto.getComplexity() != null ? dto.getComplexity() : FormComplexityLevel.SIMPLE);

        // Converter estrutura do formulário
        Map<String, Object> formStructure = Map.of(
                "tabs", dto.getTabs(),
                "actions", dto.getActions()
        );

        cleanNullValues(formStructure);
        entity.setFormStructure(formStructure);

        // Configurações adicionais
        if (dto.getCustomSlots() != null) {
            entity.setCustomSlots(dto.getCustomSlots());
        }

        if (dto.getTableColumns() != null) {
            entity.setTableColumns(Map.of("columns", dto.getTableColumns()));
        }

        entity.setJavaExtensionClass(dto.getJavaExtensionClass());
        entity.setActive(true);

        // Configurações do diálogo
        if (dto.getDialogConfig() != null && !dto.getDialogConfig().isEmpty()) {
            entity.setDialogConfig(dto.getDialogConfig());
        }

        // TODO: Adicionar informações de auditoria (createdBy, updatedBy)

        DynamicFormDefinition saved = repository.save(entity);

        // Invalidar cache
        cache.invalidate(saved.getEntityType());

        return saved;
    }

    private void normalizeReadOnly(FormDefinitionDTO dto) {
        if (dto == null || dto.getTabs() == null) {
            return;
        }

        dto.getTabs().forEach(tab -> {
            if (tab == null || tab.getFields() == null) {
                return;
            }

            tab.getFields().forEach(field -> {
                if (field == null || field.getReadOnly() != null) {
                    return;
                }

                Map<String, Object> props = field.getProps();
                if (props == null || props.isEmpty()) {
                    return;
                }

                Object readOnly = props.get("readonly");
                if (readOnly == null) {
                    readOnly = props.get("readOnly");
                }

                if (readOnly instanceof Boolean boolVal) {
                    field.setReadOnly(boolVal);
                } else if (readOnly instanceof String strVal) {
                    field.setReadOnly(Boolean.parseBoolean(strVal));
                }
            });
        });
    }


    public List<Map<String, String>> getAvailableEntityTypes() {
        // Buscar definições ativas para obter programNames quando disponíveis
        Map<String, String> programNames = repository.findByActiveTrue().stream()
                .collect(Collectors.toMap(
                        DynamicFormDefinition::getEntityType,
                        def -> def.getProgramName() != null ? def.getProgramName() : def.getEntityType(),
                        (existing, replacement) -> existing // Em caso de duplicatas, manter o primeiro
                ));

        // Retornar todas as entidades do registry com programName quando disponível
        return entityRegistryService.getAllEntities().entrySet().stream()
                .map(entry -> {
                    String entityType = entry.getKey();
                    String className = entry.getValue().getSimpleName();
                    String programName = programNames.getOrDefault(entityType, entityType);

                    return Map.of(
                            "entityType", entityType,
                            "className", className,
                            "programName", programName
                    );
                })
                .toList();
    }


    public List<Map<String, Object>> detectFieldsForEntity(String entityType) {
        Class<?> entityClass = entityRegistryService.resolveEntityClass(entityType);

        if (entityClass == null) {
            throw new IllegalArgumentException("Entidade não encontrada para o tipo: " + entityType);
        }

        List<Map<String, Object>> fields = new ArrayList<>();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isSynthetic()) continue;

            String fieldName = field.getName();
            if (List.of("serialVersionUID", "createdAt", "updatedAt").contains(fieldName)) continue;

            Map<String, Object> fieldMap = new HashMap<>();
            fieldMap.put("name", fieldName);
            fieldMap.put("type", field.getType().getSimpleName());

            fields.add(fieldMap);
        }

        return fields;
    }

    /**
     * Obtém a configuração de formulário (BaseFormConfig) para uma entidade
     */
    public BaseFormConfig getFormConfig(String entityType) {
        // Verificar cache primeiro
        BaseFormConfig cached = cache.get(entityType);
        if (cached != null) {
            log.debug("Retornando configuração do cache para: {}", entityType);
            return cached;
        }

        // Buscar no banco
        DynamicFormDefinition definition = repository.findByEntityTypeAndActiveTrue(entityType)
                .orElseThrow(() -> new FormDefinitionNotFoundException(
                        "Definição de formulário não encontrada para: " + entityType
                ));

        // Criar configuração
        BaseFormConfig config = new DynamicFormConfig(definition);

        // Adicionar ao cache
        cache.put(entityType, config);

        return config;
    }

    /**
     * Lista todas as definições ativas
     */
    @Transactional(readOnly = true)
    public List<DynamicFormDefinition> findAllActive() {
        return repository.findByActiveTrue();
    }

    /**
     * Desativa uma definição (soft delete)
     */
    public void deactivate(String entityType) {
        repository.findByEntityType(entityType).ifPresent(definition -> {
            definition.setActive(false);
            repository.save(definition);
            cache.invalidate(entityType);
        });
    }

    /**
     * Verifica se existe definição dinâmica para o tipo de entidade
     */
    public boolean existsActiveDefinition(String entityType) {
        return repository.existsByEntityTypeAndActiveTrue(entityType);
    }

    /**
     * Limpa todo o cache
     */
    public void clearCache() {
        cache.invalidateAll();
        log.info("Cache de configurações de formulário limpo");
    }
}
