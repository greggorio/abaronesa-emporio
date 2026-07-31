package com.baronesa.emporio.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.baronesa.emporio.util.ConfigManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class MPConfig {

    private final ConfigManager configManager;

    public String getAccessToken() {
        boolean isSandbox = getSandbox();
        if (isSandbox) {
            // Primeiro tenta buscar o token específico do sandbox
            String sandboxToken = configManager.getConfig("mercadopago_access_token_sandbox", "");
            if (sandboxToken != null && !sandboxToken.isEmpty()) {
                return sandboxToken;
            }
        } else {
            // Para produção, busca o token de produção
            String productionToken = configManager.getConfig("mercadopago_access_token_production", "");
            if (productionToken != null && !productionToken.isEmpty()) {
                return productionToken;
            }
        }

        // Fallback para compatibilidade com configuração antiga
        return configManager.getConfig("mercadopago_access_token", "");
    }

    public String getPublicKey() {
        boolean isSandbox = getSandbox();
        if (isSandbox) {
            // Primeiro tenta buscar a chave específica do sandbox
            String sandboxKey = configManager.getConfig("mercadopago_public_key_sandbox", "");
            if (sandboxKey != null && !sandboxKey.isEmpty()) {
                return sandboxKey;
            }
        } else {
            // Para produção, busca a chave de produção
            String productionKey = configManager.getConfig("mercadopago_public_key_production", "");
            if (productionKey != null && !productionKey.isEmpty()) {
                return productionKey;
            }
        }

        // Fallback para compatibilidade com configuração antiga
        return configManager.getConfig("mercadopago_public_key", "");
    }

    public String getBaseUrl() {
        return configManager.getConfig("mercadopago_base_url", "https://api.mercadopago.com");
    }

    public String getSandboxBaseUrl() {
        return configManager.getConfig("mercadopago_sandbox_base_url", "https://api.mercadopago.com");
    }

    public Boolean getSandbox() {
        return configManager.getBooleanConfig("mercadopago_sandbox", false);
    }

    public Integer getTimeoutConnection() {
        return configManager.getIntConfig("mercadopago_timeout_connection", 30000);
    }

    public Integer getTimeoutRead() {
        return configManager.getIntConfig("mercadopago_timeout_read", 30000);
    }

    public String getWebhookSecret() {
        return configManager.getConfig("mercadopago_webhook_secret", "");
    }

    public String getNotificationUrl() {
        // Primeiro tenta a nova configuração de webhook
        String webhookUrl = configManager.getConfig("mercadopago_webhook_url", "");
        if (!webhookUrl.isEmpty()) {
            return webhookUrl;
        }

        // Fallback para compatibilidade
        return configManager.getConfig("mercadopago_notification_url", "");
    }

    public String getApiUrl() {
        return getSandbox() ? getSandboxBaseUrl() : getBaseUrl();
    }

    // Métodos específicos para PIX
    public Integer getPixTimeoutMinutes() {
        return configManager.getIntConfig("mercadopago_pix_timeout_minutes", 10);
    }

    public String getPixMaxAmount() {
        return configManager.getConfig("mercadopago_pix_max_amount", "50000.00");
    }

    // Método para validação de webhook
    public Boolean getWebhookValidateSignature() {
        return configManager.getBooleanConfig("mercadopago_webhook_validate_signature", false);
    }
}