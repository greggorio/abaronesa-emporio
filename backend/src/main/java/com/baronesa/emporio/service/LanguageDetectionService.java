package com.baronesa.emporio.service;

import com.baronesa.emporio.util.ConfigManager;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class LanguageDetectionService {

    private final OpenAiConfigService openAiConfigService;
    private final ConfigManager configManager;

    /**
     * Detecta o idioma de um texto usando a API OpenAI
     * @param text Texto a ser analisado
     * @return Código do idioma detectado (ex: "pt", "en", "es") ou null se não for possível detectar
     */
    public String detectLanguage(String text) {
        if (!openAiConfigService.isEnabled() || text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            OpenAiService service = openAiConfigService.createOpenAiService();
            if (service == null) {
                log.warn("Serviço OpenAI não configurado para detecção de idioma");
                return null;
            }

            var config = openAiConfigService.getConfig();

            String prompt = """
                Detect the language of the following text and respond with only the language code (e.g., 'pt', 'en', 'es', 'fr', 'de', 'it', 'ja', 'ko', 'ru', 'zh').
                Do not include any other text, explanation, or punctuation. Just the language code.
                
                Text: %s
                """.formatted(text);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(config.getModel())
                    .messages(Arrays.asList(
                            new ChatMessage("system", 
                                "You are a language detection expert. Respond with only the ISO 639-1 language code."),
                            new ChatMessage("user", prompt)
                    ))
                    .maxTokens(10) // Apenas o código do idioma
                    .temperature(0.0) // Resposta mais determinística
                    .build();

            ChatCompletionResult result = service.createChatCompletion(request);
            String detectedLanguage = result.getChoices().get(0).getMessage().getContent().trim();

            // Limpar possíveis caracteres extras
            detectedLanguage = detectedLanguage.replaceAll("[^a-zA-Z]", "").toLowerCase();
            
            log.debug("Idioma detectado para texto '{}...' é: {}", text.substring(0, Math.min(50, text.length())), detectedLanguage);
            return detectedLanguage;

        } catch (Exception e) {
            log.warn("Falha ao detectar idioma do texto: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Traduz um texto para o idioma principal configurado no ERP
     * @param text Texto a ser traduzido
     * @return Texto traduzido ou o texto original em caso de falha
     */
    public String translateToMainLanguage(String text) {
        if (!openAiConfigService.isEnabled() || text == null || text.trim().isEmpty()) {
            return text;
        }

        try {
            OpenAiService service = openAiConfigService.createOpenAiService();
            if (service == null) {
                log.warn("Serviço OpenAI não configurado para tradução");
                return text;
            }

            var config = openAiConfigService.getConfig();
            String mainLanguage = configManager.getConfig("erp_language", "pt_BR");
            String languageCode = getLanguageCode(mainLanguage);

            String prompt = """
                Translate the following text to %s.
                Output ONLY the translated text, no quotes, no prefixes, no suffixes.
                Preserve any special characters, punctuation, and capitalization as much as possible.
                Source text:
                %s
                """.formatted(getLanguageName(languageCode), text);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(config.getModel())
                    .messages(Arrays.asList(
                            new ChatMessage("system",
                                "You are a professional translator. Return only the translated text with no additional commentary."),
                            new ChatMessage("user", prompt)
                    ))
                    .maxTokens(config.getMaxTokens())
                    .temperature(0.3)
                    .build();

            ChatCompletionResult result = service.createChatCompletion(request);
            String translatedText = result.getChoices().get(0).getMessage().getContent().trim();

            log.debug("Texto traduzido de '{}' para '{}'", text, translatedText);
            return translatedText;

        } catch (Exception e) {
            log.warn("Falha ao traduzir texto: {}", e.getMessage());
            return text; // Retorna o texto original em caso de falha
        }
    }

    /**
     * Verifica se o idioma do texto é diferente do idioma principal do ERP
     * @param text Texto a ser verificado
     * @return true se o idioma for diferente, false caso contrário
     */
    public boolean isDifferentFromMainLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String detectedLanguage = detectLanguage(text);
        if (detectedLanguage == null) {
            return false; // Não foi possível detectar o idioma
        }

        String mainLanguage = configManager.getConfig("erp_language", "pt_BR");
        String mainLanguageCode = getLanguageCode(mainLanguage);

        boolean isDifferent = !detectedLanguage.equalsIgnoreCase(mainLanguageCode);
        log.debug("Comparação de idiomas - Detectado: {}, Principal: {}, É diferente: {}", 
                 detectedLanguage, mainLanguageCode, isDifferent);

        return isDifferent;
    }

    /**
     * Extrai o código do idioma do formato completo (ex: pt_BR -> pt)
     * @param localeTag Tag de localização completa
     * @return Código do idioma (duas letras)
     */
    private String getLanguageCode(String localeTag) {
        if (localeTag == null) return "pt";
        return localeTag.split("_")[0].toLowerCase();
    }

    /**
     * Obtém o nome completo do idioma a partir do código
     * @param languageCode Código do idioma (ex: pt, en, es)
     * @return Nome completo do idioma em inglês
     */
    private String getLanguageName(String languageCode) {
        if (languageCode == null) return "Portuguese";
        
        return switch (languageCode.toLowerCase()) {
            case "pt" -> "Portuguese";
            case "en" -> "English";
            case "es" -> "Spanish";
            case "fr" -> "French";
            case "de" -> "German";
            case "it" -> "Italian";
            case "ja" -> "Japanese";
            case "ko" -> "Korean";
            case "ru" -> "Russian";
            case "zh" -> "Chinese";
            default -> "Portuguese"; // Padrão para português
        };
    }
}