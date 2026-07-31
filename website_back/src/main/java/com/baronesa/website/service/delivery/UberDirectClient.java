package com.baronesa.website.service.delivery;

import com.baronesa.website.dto.uber.UberDeliveryRequest;
import com.baronesa.website.dto.uber.UberDeliveryResponse;
import com.baronesa.website.dto.uber.UberQuoteResponse;
import com.baronesa.website.dto.uber.UberTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Instant;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class UberDirectClient {

    private final UberConfigService uberConfigService;
    private final WebClient webClient = WebClient.builder().build();

    private String cachedToken;
    private Instant cachedTokenExpiry;

    public UberDeliveryResponse createDelivery(UberDeliveryRequest request) {
        var config = uberConfigService.getConfig();
        String token = getAccessToken(config);
        String url = String.format("%s/v1/customers/%s/deliveries", config.apiBaseUrl(), config.customerId());

        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchangeToMono(this::handleDeliveryResponse)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(400))
                        .filter(this::isRetryable)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .block();
    }

    public UberQuoteResponse createQuote(UberDeliveryRequest request) {
        var config = uberConfigService.getConfig();
        String token = getAccessToken(config);
        String url = String.format("%s/v1/customers/%s/delivery_quotes", config.apiBaseUrl(), config.customerId());

        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchangeToMono(this::handleQuoteResponse)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(400))
                        .filter(this::isRetryable)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .block();
    }

    public void markPickupReady(String deliveryId) {
        var config = uberConfigService.getConfig();
        String token = getAccessToken(config);
        String path = config.pickupReadyPath();
        String resolved = String.format(path, config.customerId(), deliveryId);
        String url = config.apiBaseUrl() + resolved;

        Instant readyAt = Instant.now().plusSeconds(120);

        webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PickupReadyPayload(readyAt.toString()))
                .exchangeToMono(this::handlePickupReadyResponse)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(400))
                        .filter(this::isRetryable)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .block();
    }

    private Mono<UberDeliveryResponse> handleDeliveryResponse(ClientResponse response) {
        if (response.statusCode().isError()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> {
                        log.warn("Uber create delivery error status={} body={}", response.statusCode(), body);
                        return response.createException().flatMap(Mono::error);
                    });
        }
        return response.bodyToMono(UberDeliveryResponse.class);
    }

    private Mono<UberQuoteResponse> handleQuoteResponse(ClientResponse response) {
        if (response.statusCode().isError()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> {
                        log.warn("Uber quote error status={} body={}", response.statusCode(), body);
                        return response.createException().flatMap(Mono::error);
                    });
        }
        return response.bodyToMono(UberQuoteResponse.class);
    }

    private Mono<Void> handlePickupReadyResponse(ClientResponse response) {
        if (response.statusCode().isError()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> {
                        log.warn("Uber pickup ready error status={} body={}", response.statusCode(), body);
                        return response.createException().flatMap(Mono::error);
                    });
        }
        return response.bodyToMono(Void.class);
    }

    private record PickupReadyPayload(String pickup_ready_dt) {}

    private synchronized String getAccessToken(com.baronesa.website.dto.erp.UberConfigResponse config) {
        if (config.accessToken() != null && !config.accessToken().isBlank()) {
            return config.accessToken();
        }

        if (cachedToken != null && cachedTokenExpiry != null && cachedTokenExpiry.isAfter(Instant.now().plusSeconds(30))) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("grant_type", "client_credentials");
        form.add("scope", config.scope());

        UberTokenResponse tokenResponse = webClient.post()
                .uri(config.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(UberTokenResponse.class)
                .block();

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new IllegalStateException("Falha ao obter token Uber");
        }

        cachedToken = tokenResponse.getAccessToken();
        long expiresIn = tokenResponse.getExpiresIn() == null ? 0 : tokenResponse.getExpiresIn();
        cachedTokenExpiry = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }

    private boolean isRetryable(Throwable throwable) {
        return throwable instanceof WebClientRequestException;
    }
}
