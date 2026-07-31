package com.baronesa.emporio.config;

import org.springframework.stereotype.Component;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MercadoPagoProperties {

    private final ConfigManager configManager;

    // Métodos para obter chaves atuais baseado no ambiente
    public String getCurrentAccessToken() {
        return isSandbox() ? getAccessTokenSandbox() : getAccessTokenProduction();
    }

    public String getCurrentPublicKey() {
        return isSandbox() ? getPublicKeySandbox() : getPublicKeyProduction();
    }

    // Getters que buscam do ConfigManager
    public String getAccessTokenSandbox() {
        return configManager.getConfig("mercadopago_access_token_sandbox", "");
    }

    public String getAccessTokenProduction() {
        return configManager.getConfig("mercadopago_access_token_production", "");
    }

    public String getPublicKeySandbox() {
        return configManager.getConfig("mercadopago_public_key_sandbox", "");
    }

    public String getPublicKeyProduction() {
        return configManager.getConfig("mercadopago_public_key_production", "");
    }

    public boolean isSandbox() {
        return configManager.getBooleanConfig("mercadopago_sandbox", true);
    }

    public String getWebhookUrl() {
        return configManager.getConfig("mercadopago_webhook_url", "");
    }

    public Webhook getWebhook() {
        return new Webhook();
    }

    // Setters que atualizam no ConfigManager
    public void setAccessTokenSandbox(String accessTokenSandbox) {
        configManager.setConfig("mercadopago_access_token_sandbox", accessTokenSandbox);
    }

    public void setAccessTokenProduction(String accessTokenProduction) {
        configManager.setConfig("mercadopago_access_token_production", accessTokenProduction);
    }

    public void setPublicKeySandbox(String publicKeySandbox) {
        configManager.setConfig("mercadopago_public_key_sandbox", publicKeySandbox);
    }

    public void setPublicKeyProduction(String publicKeyProduction) {
        configManager.setConfig("mercadopago_public_key_production", publicKeyProduction);
    }

    public void setSandbox(boolean sandbox) {
        configManager.setConfig("mercadopago_sandbox", String.valueOf(sandbox));
    }

    public void setWebhookUrl(String webhookUrl) {
        configManager.setConfig("mercadopago_webhook_url", webhookUrl);
    }

    public void setWebhook(Webhook webhook) {
        if (webhook != null) {
            configManager.setConfig("mercadopago_webhook_validate_signature", String.valueOf(webhook.isValidateSignature()));
            if (webhook.getSecret() != null) {
                configManager.setConfig("mercadopago_webhook_secret", webhook.getSecret());
            }
        }
    }

    // Classe interna para configurações de webhook
    public class Webhook {

        public boolean isValidateSignature() {
            return configManager.getBooleanConfig("mercadopago_webhook_validate_signature", false);
        }

        public void setValidateSignature(boolean validateSignature) {
            configManager.setConfig("mercadopago_webhook_validate_signature", String.valueOf(validateSignature));
        }

        public String getSecret() {
            return configManager.getConfig("mercadopago_webhook_secret", "");
        }

        public void setSecret(String secret) {
            configManager.setConfig("mercadopago_webhook_secret", secret != null ? secret : "");
        }

        @Override
        public String toString() {
            return "Webhook{" +
                    "validateSignature=" + isValidateSignature() +
                    ", secret='" + (getSecret() != null && !getSecret().isEmpty() ? "***" : "null") + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "MercadoPagoProperties{" +
                "sandbox=" + isSandbox() +
                ", webhookUrl='" + getWebhookUrl() + '\'' +
                ", accessTokenSandbox='" + (getAccessTokenSandbox() != null && !getAccessTokenSandbox().isEmpty() ? "***" : "null") + '\'' +
                ", accessTokenProduction='" + (getAccessTokenProduction() != null && !getAccessTokenProduction().isEmpty() ? "***" : "null") + '\'' +
                ", webhook=" + getWebhook() +
                '}';
    }
}