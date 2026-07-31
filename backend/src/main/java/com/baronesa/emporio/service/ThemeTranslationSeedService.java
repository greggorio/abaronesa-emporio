package com.baronesa.emporio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeTranslationSeedService {

    private final EspressoThemeClient espressoThemeClient;
    private final ThemeTranslationMapper themeTranslationMapper;

    @Value("${theme.translation.default-tenant:espresso}")
    private String defaultTenantId;

    /**
     * Busca o tema ativo e garante entradas PENDING no entity_translation para os locais alvo.
     *
     * @return 1 se processou o tema, 0 caso contrário
     */
    public int seedTranslationsForActiveTheme() {
        try {
            if (defaultTenantId == null || defaultTenantId.isBlank()) {
                log.warn("Tenant padrão para seed de tema não configurado (theme.translation.default-tenant)");
                return 0;
            }

            Map<String, Object> theme = espressoThemeClient.getActiveTheme(defaultTenantId, null);
            if (theme == null || theme.isEmpty()) {
                log.warn("Tema ativo não encontrado para tenant {}", defaultTenantId);
                return 0;
            }

            // Locale PT força fallback, mas markSourceChanged cria/atualiza PENDING para target-locales
            themeTranslationMapper.translateTheme(theme, Locale.forLanguageTag("pt-BR"), true);
            log.info("Seed de traduções do tema concluído para tenant {}", defaultTenantId);
            return 1;
        } catch (Exception e) {
            log.error("Erro ao semear traduções do tema ativo", e);
            return 0;
        }
    }
}
