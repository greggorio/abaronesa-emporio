package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.openai.OpenAiConfigDTO;
import com.baronesa.emporio.util.ConfigManager;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;

/**
 * Serviço para gerenciar configurações OpenAI dinâmicas
 * Lê configurações do ConfigManager (tabela configuracoes)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiConfigService {

    private final ConfigManager configManager;

    // Chave para criptografia (16 bytes para AES-128)
    private static final String ENCRYPTION_KEY = "BakeryAIKey12345"; // 16 caracteres

    /**
     * Busca as configurações OpenAI do ConfigManager
     * Retorna configuração padrão se não houver configurações
     */
    public OpenAiConfigDTO getConfig() {
        String apiKey = configManager.getConfig("openai_api_key", "");
        String model = configManager.getConfig("openai_model", "gpt-3.5-turbo");
        Integer maxTokens = configManager.getIntConfig("openai_max_tokens", 500);
        Integer timeout = configManager.getIntConfig("openai_timeout", 30);
        Boolean habilitado = configManager.getBooleanConfig("openai_habilitado", false);

        // Se não tiver API key ou não estiver habilitado, retornar config desabilitada
        if (apiKey.isEmpty() || !habilitado) {
            return OpenAiConfigDTO.builder()
                    .apiKey("")
                    .model(model)
                    .maxTokens(maxTokens)
                    .timeout(timeout)
                    .habilitado(false)
                    .build();
        }

        // Descriptografar API key
        String decryptedApiKey = decryptApiKey(apiKey);

        return OpenAiConfigDTO.builder()
                .apiKey(decryptedApiKey)
                .model(model)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .habilitado(habilitado)
                .build();
    }

    /**
     * Salva as configurações OpenAI no ConfigManager
     */
    public boolean saveConfig(OpenAiConfigDTO config) {
        try {
            // Criptografar API key antes de salvar
            if (config.getApiKey() != null && !config.getApiKey().isEmpty() && !"********".equals(config.getApiKey())) {
                String encryptedApiKey = encryptApiKey(config.getApiKey());
                configManager.setConfig("openai_api_key", encryptedApiKey);
            }

            configManager.setConfig("openai_model", config.getModel());
            configManager.setConfig("openai_max_tokens", String.valueOf(config.getMaxTokens()));
            configManager.setConfig("openai_timeout", String.valueOf(config.getTimeout()));
            configManager.setConfig("openai_habilitado", String.valueOf(config.getHabilitado()));

            return true;
        } catch (Exception e) {
            log.error("Erro ao salvar configurações OpenAI", e);
            return false;
        }
    }

    /**
     * Cria um OpenAiService com as configurações atuais
     * Retorna null se não houver configurações válidas
     */
    public OpenAiService createOpenAiService() {
        OpenAiConfigDTO config = getConfig();

        if (!config.getHabilitado() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            log.warn("OpenAI não configurada ou desabilitada");
            return null;
        }

        try {
            return new OpenAiService(config.getApiKey(), Duration.ofSeconds(config.getTimeout()));
        } catch (Exception e) {
            log.error("Erro ao criar OpenAiService", e);
            return null;
        }
    }

    /**
     * Verifica se o serviço OpenAI está habilitado e configurado
     */
    public boolean isEnabled() {
        OpenAiConfigDTO config = getConfig();
        return config.getHabilitado() && config.getApiKey() != null && !config.getApiKey().isEmpty();
    }

    /**
     * Testa a conexão com a API OpenAI
     */
    public boolean testConnection(OpenAiConfigDTO config) {
        try {
            // Se a API key vier como "********", usar a atual
            String apiKey = config.getApiKey();
            if ("********".equals(apiKey)) {
                OpenAiConfigDTO currentConfig = getConfig();
                apiKey = currentConfig.getApiKey();
            }

            if (apiKey == null || apiKey.isEmpty()) {
                return false;
            }

            // Criar serviço temporário para teste
            OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(config.getTimeout()));

            // Fazer uma chamada simples para testar
            service.listModels();

            return true;
        } catch (Exception e) {
            log.error("Erro ao testar conexão OpenAI: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Criptografa a API key usando AES
     */
    private String encryptApiKey(String apiKey) {
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(apiKey.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Erro ao criptografar API key", e);
            return apiKey; // Fallback: retornar original
        }
    }

    /**
     * Descriptografa a API key usando AES
     */
    private String decryptApiKey(String encryptedApiKey) {
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = Base64.getDecoder().decode(encryptedApiKey);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted);
        } catch (Exception e) {
            log.error("Erro ao descriptografar API key", e);
            return ""; // Fallback: retornar vazio
        }
    }

    /**
     * Testa um prompt simples com a OpenAI
     * @param prompt Pergunta/prompt para testar
     * @return Resposta da OpenAI
     */
    public String testPrompt(String prompt) {
        try {
            OpenAiService service = createOpenAiService();
            if (service == null) {
                throw new Exception("Serviço OpenAI não configurado");
            }

            OpenAiConfigDTO config = getConfig();

            com.theokanning.openai.completion.chat.ChatCompletionRequest request =
                com.theokanning.openai.completion.chat.ChatCompletionRequest.builder()
                    .model(config.getModel())
                    .messages(java.util.Arrays.asList(
                        new com.theokanning.openai.completion.chat.ChatMessage("system", "Você é um assistente útil e amigável."),
                        new com.theokanning.openai.completion.chat.ChatMessage("user", prompt)
                    ))
                    .maxTokens(config.getMaxTokens())
                    .temperature(0.7)
                    .build();

            com.theokanning.openai.completion.chat.ChatCompletionResult result = service.createChatCompletion(request);
            return result.getChoices().get(0).getMessage().getContent().trim();

        } catch (Exception e) {
            log.error("Erro ao testar prompt: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao comunicar com OpenAI: " + e.getMessage());
        }
    }
}
