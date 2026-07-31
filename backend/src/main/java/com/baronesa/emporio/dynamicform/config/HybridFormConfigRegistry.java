package com.baronesa.emporio.dynamicform.config;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.service.DynamicFormService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry que combina configurações estáticas (Java) e dinâmicas (banco de dados)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HybridFormConfigRegistry {

    private final DynamicFormService dynamicFormService;
    private final ApplicationContext applicationContext;

    // Cache de configurações estáticas
    private final Map<String, BaseFormConfig> staticConfigs = new HashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Inicializando HybridFormConfigRegistry");

        // Registrar configurações estáticas conhecidas
        // TODO: Usar reflection ou anotações para descobrir automaticamente
        //registerStaticConfig("produtos", ProdutoFormConfig.getInstance());
        //registerStaticConfig("clientes", ClienteFormConfig.getInstance());
        //registerStaticConfig("contas-pagar", ContaPagarFormConfig.getInstance());
        //registerStaticConfig("recebimentos", RecebimentoMercadoriaFormConfig.getInstance());
        //registerStaticConfig("movimento-estoque", MovimentoEstoqueFormConfig.getInstance());
        // Adicionar outras conforme necessário

        log.info("Registradas {} configurações estáticas", staticConfigs.size());
    }

    private void registerStaticConfig(String entityType, BaseFormConfig config) {
        staticConfigs.put(entityType, config);
    }

    /**
     * Obtém configuração de formulário, priorizando dinâmica sobre estática
     */
    public BaseFormConfig getConfig(String entityType) {
        System.out.println("################ procurando definição para "+ entityType);
        // 1. Primeiro verifica se existe configuração dinâmica
        if (dynamicFormService.existsActiveDefinition(entityType)) {
            try {
                log.debug("Usando configuração dinâmica para: {}", entityType);
                return dynamicFormService.getFormConfig(entityType);
            } catch (Exception e) {
                log.error("Erro ao carregar configuração dinâmica para: {}. Tentando estática.", entityType, e);
            }
        }

        // 2. Se não houver dinâmica ou houver erro, usa estática
        BaseFormConfig staticConfig = staticConfigs.get(entityType);
        if (staticConfig != null) {
            log.debug("Usando configuração estática para: {}", entityType);
            return staticConfig;
        }

        // 3. Se não encontrar nenhuma, lança exceção
        throw new IllegalArgumentException("Nenhuma configuração encontrada para entidade: " + entityType);
    }

    /**
     * Verifica se existe alguma configuração (dinâmica ou estática) para o tipo
     */
    public boolean hasConfig(String entityType) {
        return dynamicFormService.existsActiveDefinition(entityType) ||
                staticConfigs.containsKey(entityType);
    }

    /**
     * Lista todos os tipos de entidade disponíveis
     */
    public Map<String, String> getAllAvailableTypes() {
        Map<String, String> types = new HashMap<>();

        // Adicionar tipos dinâmicos
        dynamicFormService.findAllActive().forEach(def -> {
            types.put(def.getEntityType(), def.getProgramName() + " (Dinâmico)");
        });

        // Adicionar tipos estáticos
        staticConfigs.forEach((type, config) -> {
            if (!types.containsKey(type)) {
                types.put(type, config.getProgramName() + " (Estático)");
            }
        });

        return types;
    }

    /**
     * Força recarga de uma configuração específica
     */
    public void reloadConfig(String entityType) {
        dynamicFormService.clearCache();
        log.info("Cache limpo para entidade: {}", entityType);
    }
}