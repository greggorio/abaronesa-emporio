package com.baronesa.emporio.dynamicform.config;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.service.DynamicFormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Registry para configurações dinâmicas de formulário (100% banco de dados)
 * Substitui o HybridFormConfigRegistry
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicFormRegistry {

    private final DynamicFormService dynamicFormService;

    /**
     * Obtém configuração de formulário do banco de dados
     */
    public BaseFormConfig getConfig(String entityType) {
        log.debug("Buscando configuração dinâmica para: {}", entityType);
        return dynamicFormService.getFormConfig(entityType);
    }

    /**
     * Verifica se existe configuração para o tipo de entidade
     */
    public boolean hasConfig(String entityType) {
        return dynamicFormService.existsActiveDefinition(entityType);
    }

    /**
     * Força recarga do cache para uma configuração específica
     */
    public void reloadConfig(String entityType) {
        dynamicFormService.clearCache();
        log.info("Cache limpo para entidade: {}", entityType);
    }
}