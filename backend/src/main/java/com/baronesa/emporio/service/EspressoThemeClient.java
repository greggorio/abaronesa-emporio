package com.baronesa.emporio.service;

import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EspressoThemeClient {

    private static final String ESPRESSO_API_BASE_URL_KEY = "espresso-api.app.base-url";

    private final ConfigManager configManager;
    private final RestClient restClient = RestClient.create();

    @Value("${espresso-api.app.base-url:${ESPRESSO_API_BASE_URL:http://localhost:8085}}")
    private String espressoApiBaseUrlDefault;

    /**
     * Busca o tema ativo público no espresso_back.
     *
     * @param tenantId tenant (obrigatório)
     * @param cb       cache-buster opcional (replicado para compatibilidade)
     * @return payload do tema como Map
     */
    public Map<String, Object> getActiveTheme(String tenantId, String cb) {
        String baseUrl = resolveBaseUrl();
        if (baseUrl == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A URL do sistema Espresso não está configurada. Atualize a configuração '" + ESPRESSO_API_BASE_URL_KEY + "'.");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("tenantId", tenantId);
        if (cb != null && !cb.isBlank()) {
            params.add("_cb", cb);
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/themes/public/theme/active")
                    .queryParams(params)
                    .build()
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);
            return response;
        } catch (Exception e) {
            log.error("Erro ao buscar tema ativo no espresso (tenantId={})", tenantId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível recuperar o tema ativo do Espresso.", e);
        }
    }

    private String resolveBaseUrl() {
        String configurada = configManager.getConfig(ESPRESSO_API_BASE_URL_KEY, null);
        String baseUrl = isBlank(configurada) ? null : configurada.trim();

        if (isBlank(baseUrl) && !isBlank(espressoApiBaseUrlDefault)) {
            log.warn("Config '{}' vazia; aplicando valor padrão das propriedades e persistindo.", ESPRESSO_API_BASE_URL_KEY);
            baseUrl = espressoApiBaseUrlDefault.trim();
        }

        if (isBlank(baseUrl)) {
            log.error("URL do sistema espresso não configurada. Verifique a configuração '{}'", ESPRESSO_API_BASE_URL_KEY);
            return null;
        }

        if (isBlank(configurada) || !baseUrl.equals(configurada)) {
            configManager.setConfig(ESPRESSO_API_BASE_URL_KEY, baseUrl);
        }
        return baseUrl;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
