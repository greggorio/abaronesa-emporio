package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.EntityTranslation;
import com.baronesa.emporio.entity.TranslationStatus;
import com.baronesa.emporio.repository.EntityTranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Job simples para processar traduções pendentes.
 * Fase atual: integra com OpenAI (quando configurado); fallback SIMULATION.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationJobService {

    private final EntityTranslationRepository entityTranslationRepository;
    private final OpenAiConfigService openAiConfigService;
    private final TranslationService translationService;

    @Transactional
    public int processPendingTranslations() {
        // Antes de processar, cria entradas PENDING para novos locales configurados
        int createdMissing = translationService.ensureAllTargetLocalesPresent();

        List<EntityTranslation> pendentes = entityTranslationRepository.findAll().stream()
                .filter(t -> TranslationStatus.PENDING.equals(t.getStatus()))
                .toList();

        if (pendentes.isEmpty()) {
            return 0;
        }

        AtomicInteger processed = new AtomicInteger(0);
        pendentes.forEach(t -> {
            try {
                String translated = sanitize(translateWithProvider(t), t.getSourceText());
                t.setTranslatedText(translated);
                t.setStatus(TranslationStatus.OK);
                entityTranslationRepository.save(t);
                processed.incrementAndGet();
            } catch (Exception e) {
                log.warn("Falha ao traduzir {}:{}:{} -> {}", t.getEntityType(), t.getEntityId(), t.getField(), e.getMessage());
                t.setStatus(TranslationStatus.FAILED);
                t.setTranslatedText(null);
                entityTranslationRepository.save(t);
            }
        });
        return processed.get() + createdMissing;
    }

    private String translateWithProvider(EntityTranslation t) {
        // Se OpenAI estiver configurada, usar; senão, fallback SIMULATION
        if (openAiConfigService.isEnabled()) {
            return translateViaOpenAi(t);
        }
        t.setProvider("SIMULATION");
        return simulateTranslation(t.getSourceText());
    }

    private String translateViaOpenAi(EntityTranslation t) {
        var service = openAiConfigService.createOpenAiService();
        if (service == null) {
            throw new IllegalStateException("OpenAI não configurado");
        }
        var config = openAiConfigService.getConfig();
        String prompt = buildPrompt(t.getSourceText(), t.getLocale());

        com.theokanning.openai.completion.chat.ChatCompletionRequest request =
                com.theokanning.openai.completion.chat.ChatCompletionRequest.builder()
                        .model(config.getModel())
                        .messages(java.util.Arrays.asList(
                                new com.theokanning.openai.completion.chat.ChatMessage("system",
                                        """
                                                You are a professional menu translator for a restaurant site.
                                                Return only the translated text, with no quotes, no explanations and no metadata.
                                                If the source is already in the target language or is a proper noun/brand, return it unchanged.
                                                Preserve formatting, punctuation, accents, HTML tags and placeholders exactly as in the source.
                                                Do NOT add sentences like “in Spanish is ...” or “translates to ...”.
                                                """),
                                new com.theokanning.openai.completion.chat.ChatMessage("user", prompt)
                        ))
                        .maxTokens(config.getMaxTokens())
                        .temperature(0.2)
                        .build();

        String translated = service.createChatCompletion(request)
                .getChoices()
                .get(0)
                .getMessage()
                .getContent()
                .trim();

        t.setProvider("OPENAI");
        return translated;
    }

    private String buildPrompt(String source, String localeTag) {
        if (source == null || source.isBlank()) return "";
        Locale target = Locale.forLanguageTag(localeTag);
        String language = target.getDisplayLanguage(Locale.ENGLISH);
        return """
                Translate from Portuguese to %s.
                Output ONLY the translated text, no quotes, no prefixes, no suffixes.
                Preserve any HTML tags, placeholders, and casing.
                Source:
                %s
                """.formatted(language, source);
    }

    private String simulateTranslation(String source) {
        if (source == null) return "";
        return source;
    }

    private String sanitize(String text, String sourceFallback) {
        if (text == null) return sourceFallback == null ? "" : sourceFallback;
        String trimmed = text.trim();
        // remove aspas simples ou duplas ou aspas tipográficas se encapsularem tudo
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')
                    || (first == '“' && last == '”') || (first == '”' && last == '“')) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        if (trimmed.isBlank()) {
            return sourceFallback == null ? "" : sourceFallback;
        }
        return trimmed;
    }
}
