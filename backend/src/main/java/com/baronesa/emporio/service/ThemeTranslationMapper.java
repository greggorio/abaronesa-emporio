package com.baronesa.emporio.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ThemeTranslationMapper {

    private static final String ENTITY_TYPE = "THEME";

    private final TranslationService translationService;

    /**
     * Aplica tradução aos campos textuais do tema, reutilizando entity_translation.
     */
    public Map<String, Object> translateTheme(Map<String, Object> themePayload, Locale locale) {
        return translateTheme(themePayload, locale, false);
    }

    /**
     * @param markChanges se true, executa markSourceChanged; use apenas em jobs/offline para evitar escrita em rotas de leitura.
     */
    public Map<String, Object> translateTheme(Map<String, Object> themePayload, Locale locale, boolean markChanges) {
        if (themePayload == null) {
            return Map.of();
        }
        Long themeId = toLong(themePayload.get("id"));

        Map<String, Object> content = asMap(themePayload.get("content"));
        Map<String, Object> translatedContent = translateContent(themeId, content, locale, markChanges);

        Map<String, Object> result = new LinkedHashMap<>(themePayload);
        if (translatedContent != null) {
            result.put("content", translatedContent);
        }
        return result;
    }

    private Map<String, Object> translateContent(Long themeId, Map<String, Object> content, Locale locale, boolean markChanges) {
        if (content == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>(content);

        translateStringField(themeId, "name", result, "name", locale, markChanges);
        translateStringField(themeId, "seoTitle", result, "seoTitle", locale, markChanges);
        translateStringField(themeId, "seoSiteName", result, "seoSiteName", locale, markChanges);
        translateStringField(themeId, "seoAuthor", result, "seoAuthor", locale, markChanges);

        translateStringField(themeId, "heroTitle", result, "heroTitle", locale, markChanges);
        translateStringField(themeId, "heroSubtitle", result, "heroSubtitle", locale, markChanges);
        translateStringField(themeId, "heroCtaText", result, "heroCtaText", locale, markChanges);
        translateStringField(themeId, "heroSecondaryCtaText", result, "heroSecondaryCtaText", locale, markChanges);

        translateStringField(themeId, "businessType", result, "businessType", locale, markChanges);

        translateListField(themeId, "navItems", "label", "navItems.label", result, locale, markChanges);
        translateListField(themeId, "heroCards", "title", "heroCards.title", result, locale, markChanges);
        translateListField(themeId, "heroCards", "description", "heroCards.description", result, locale, markChanges);

        translateStringField(themeId, "aboutTitle", result, "aboutTitle", locale, markChanges);
        translateStringField(themeId, "aboutDescription1", result, "aboutDescription1", locale, markChanges);
        translateStringField(themeId, "aboutDescription2", result, "aboutDescription2", locale, markChanges);
        translateStringField(themeId, "aboutAddress", result, "aboutAddress", locale, markChanges);

        translateListField(themeId, "aboutFeatures", "title", "aboutFeatures.title", result, locale, markChanges);
        translateListField(themeId, "aboutFeatures", "description", "aboutFeatures.description", result, locale, markChanges);

        translateListField(themeId, "aboutHours", "days", "aboutHours.days", result, locale, markChanges);
        translateListField(themeId, "aboutHours", "hours", "aboutHours.hours", result, locale, markChanges);

        return result;
    }

    private void translateStringField(Long themeId,
                                      String key,
                                      Map<String, Object> target,
                                      String fieldPath,
                                      Locale locale,
                                      boolean markChanges) {
        Object value = target.get(key);
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return;
        }
        if (markChanges) {
            translationService.markSourceChanged(ENTITY_TYPE, themeId, fieldPath, text);
        }
        String translated = translationService.translate(ENTITY_TYPE, themeId, fieldPath, text, locale);
        target.put(key, translated);
    }

    private void translateListField(Long themeId,
                                    String listKey,
                                    String itemField,
                                    String fieldPathBase,
                                    Map<String, Object> target,
                                    Locale locale,
                                    boolean markChanges) {
        Object listObj = target.get(listKey);
        if (!(listObj instanceof List<?> list)) {
            return;
        }
        List<Object> translatedItems = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            Object itemObj = list.get(index);
            Map<String, Object> itemMap = asMap(itemObj);
            if (itemMap == null) {
                translatedItems.add(itemObj);
                continue;
            }
            Map<String, Object> updated = new LinkedHashMap<>(itemMap);
            Object value = updated.get(itemField);
            if (value instanceof String text && StringUtils.hasText(text)) {
                String fieldPath = fieldPathBase + "[" + index + "]";
                if (markChanges) {
                    translationService.markSourceChanged(ENTITY_TYPE, themeId, fieldPath, text);
                }
                String translated = translationService.translate(ENTITY_TYPE, themeId, fieldPath, text, locale);
                updated.put(itemField, translated);
            }
            translatedItems.add(updated);
        }
        target.put(listKey, translatedItems);
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(Objects.toString(k, null), v));
            return result;
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && StringUtils.hasText(s)) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
