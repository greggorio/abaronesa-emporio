package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.EntityTranslation;
import com.baronesa.emporio.entity.TranslationStatus;
import com.baronesa.emporio.repository.EntityTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TranslationService {

    private static final List<TranslationStatus> ACTIVE_STATUSES = List.of(TranslationStatus.OK, TranslationStatus.MANUAL);

    private final EntityTranslationRepository repository;

    @Value("${translation.target-locales:en-US}")
    private String targetLocalesProperty;

    public String translate(String entityType, Long entityId, String field, String sourceText, Locale locale) {
        if (entityId == null) {
            return sourceText;
        }
        if (locale == null || locale.getLanguage() == null || locale.getLanguage().isBlank()) {
            return sourceText;
        }
        String localeTag = locale.toLanguageTag();

        // Fallback para PT: não traduz
        if (locale.getLanguage().equalsIgnoreCase("pt")) {
            return sourceText;
        }

        Optional<EntityTranslation> translation = repository.findOneActive(entityType, entityId, field, localeTag, ACTIVE_STATUSES);
        return translation.map(t -> {
            String text = t.getTranslatedText();
            return (text == null || text.isBlank()) ? sourceText : text;
        }).orElse(sourceText);
    }

    /**
        * Utilitário para futuras fases: calcular hash do texto fonte.
        */
    public String computeHash(String sourceText) {
        if (sourceText == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sourceText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * Marca traduções existentes como pendentes quando o texto fonte muda.
     * Cria entradas pendentes para todos os locales alvo configurados.
     */
    public void markSourceChanged(String entityType, Long entityId, String field, String newSourceText) {
        if (entityId == null) return;
        String newHash = computeHash(newSourceText);
        List<String> targetLocales = getTargetLocales();
        List<EntityTranslation> translations = repository.findByEntityTypeAndEntityIdAndField(entityType, entityId, field);
        Map<String, EntityTranslation> byLocale = translations.stream()
                .filter(t -> t.getLocale() != null)
                .collect(Collectors.toMap(EntityTranslation::getLocale, Function.identity(), (a, b) -> a));

        boolean changed = false;
        for (EntityTranslation t : translations) {
            if (TranslationStatus.MANUAL.equals(t.getStatus())) {
                continue;
            }
            if (!Objects.equals(t.getSourceHash(), newHash)) {
                t.setSourceText(newSourceText);
                t.setSourceHash(newHash);
                t.setStatus(TranslationStatus.PENDING);
                t.setProvider(null);
                changed = true;
            }
        }

        for (String localeTag : targetLocales) {
            if (!StringUtils.hasText(localeTag)) {
                continue;
            }
            EntityTranslation existing = byLocale.get(localeTag);
            if (existing == null) {
                EntityTranslation novo = new EntityTranslation();
                novo.setEntityType(entityType);
                novo.setEntityId(entityId);
                novo.setField(field);
                novo.setLocale(localeTag);
                novo.setSourceText(newSourceText);
                novo.setSourceHash(newHash);
                novo.setStatus(TranslationStatus.PENDING);
                translations.add(novo);
                byLocale.put(localeTag, novo);
                changed = true;
            }
        }
        if (changed) {
            repository.saveAll(translations);
        }
    }

    private List<String> getTargetLocales() {
        if (!StringUtils.hasText(targetLocalesProperty)) {
            return List.of();
        }
        return Arrays.stream(targetLocalesProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * Garante que cada combinação (entityType, entityId, field) possua linhas
     * para todos os locales alvo. Útil quando adicionamos um novo locale na config
     * e queremos gerar PENDING automaticamente sem precisar editar cada entidade.
     *
     * @return quantidade de traduções criadas
     */
    public int ensureAllTargetLocalesPresent() {
        List<String> targetLocales = getTargetLocales();
        if (targetLocales.isEmpty()) {
            return 0;
        }

        List<EntityTranslation> all = repository.findAll();
        if (all.isEmpty()) {
            return 0;
        }

        record Key(String entityType, Long entityId, String field) {}

        Map<Key, List<EntityTranslation>> grouped = all.stream()
                .filter(t -> t.getEntityType() != null && t.getEntityId() != null && t.getField() != null)
                .collect(Collectors.groupingBy(t -> new Key(t.getEntityType(), t.getEntityId(), t.getField())));

        List<EntityTranslation> toCreate = grouped.entrySet().stream()
                .flatMap(entry -> {
                    Key key = entry.getKey();
                    List<EntityTranslation> translations = entry.getValue();
                    Map<String, EntityTranslation> byLocale = translations.stream()
                            .filter(t -> t.getLocale() != null)
                            .collect(Collectors.toMap(EntityTranslation::getLocale, Function.identity(), (a, b) -> a));

                    // Usa qualquer sourceText/hash disponível como base
                    String sourceText = translations.stream()
                            .map(EntityTranslation::getSourceText)
                            .filter(StringUtils::hasText)
                            .findFirst()
                            .orElse(null);
                    String sourceHash = translations.stream()
                            .map(EntityTranslation::getSourceHash)
                            .filter(StringUtils::hasText)
                            .findFirst()
                            .orElse(computeHash(sourceText));

                    return targetLocales.stream()
                            .filter(StringUtils::hasText)
                            .filter(locale -> !byLocale.containsKey(locale))
                            .map(locale -> {
                                EntityTranslation novo = new EntityTranslation();
                                novo.setEntityType(key.entityType());
                                novo.setEntityId(key.entityId());
                                novo.setField(key.field());
                                novo.setLocale(locale);
                                novo.setSourceText(sourceText);
                                novo.setSourceHash(sourceHash);
                                novo.setStatus(TranslationStatus.PENDING);
                                return novo;
                            });
                })
                .toList();

        if (!toCreate.isEmpty()) {
            repository.saveAll(toCreate);
        }
        return toCreate.size();
    }
}
