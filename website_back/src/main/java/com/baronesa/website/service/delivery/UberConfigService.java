package com.baronesa.website.service.delivery;

import com.baronesa.website.config.ErpConfig;
import com.baronesa.website.config.UberProperties;
import com.baronesa.website.dto.erp.UberConfigResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class UberConfigService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ErpConfig erpConfig;
    private final UberProperties fallbackProperties;
    private final WebClient webClient = WebClient.builder().build();

    @Value("${website.sync.api-key:}")
    private String websiteSyncApiKey;

    private volatile UberConfigResponse cachedConfig;
    private volatile Instant cachedAt;

    public UberConfigResponse getConfig() {
        UberConfigResponse cached = cachedConfig;
        if (cached != null && cachedAt != null && cachedAt.isAfter(Instant.now().minus(CACHE_TTL))) {
            return cached;
        }
        return refreshConfig();
    }

    private synchronized UberConfigResponse refreshConfig() {
        if (cachedConfig != null && cachedAt != null && cachedAt.isAfter(Instant.now().minus(CACHE_TTL))) {
            return cachedConfig;
        }

        UberConfigResponse fetched = fetchFromErp();
        if (fetched != null) {
            cachedConfig = fetched;
            cachedAt = Instant.now();
            return fetched;
        }

        UberConfigResponse fallback = fallbackFromLocal();
        cachedConfig = fallback;
        cachedAt = Instant.now();
        return fallback;
    }

    private UberConfigResponse fetchFromErp() {
        String apiUrl = erpConfig.getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return null;
        }
        try {
            String url = apiUrl + "/api/integration/configs/uber";
            return webClient.get()
                    .uri(url)
                    .header("X-ERP-KEY", websiteSyncApiKey)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .bodyToMono(UberConfigResponse.class)
                    .block();
        } catch (Exception ex) {
            log.warn("Falha ao buscar configuracoes Uber no ERP, usando fallback local. erro={}", ex.getMessage());
            return null;
        }
    }

    private UberConfigResponse fallbackFromLocal() {
        return new UberConfigResponse(
                fallbackProperties.getClientId(),
                fallbackProperties.getClientSecret(),
                fallbackProperties.getCustomerId(),
                fallbackProperties.getScope(),
                fallbackProperties.getAccessToken(),
                fallbackProperties.getTokenUrl(),
                fallbackProperties.getApiBaseUrl(),
                fallbackProperties.getPickupAddress(),
                fallbackProperties.getPickupName(),
                fallbackProperties.getPickupPhone(),
                fallbackProperties.getPickupNotes(),
                fallbackProperties.getPickupReadyPath()
        );
    }
}
