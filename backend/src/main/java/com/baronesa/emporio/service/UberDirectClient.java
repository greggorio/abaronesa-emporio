package com.baronesa.emporio.service;

import com.baronesa.emporio.config.UberProperties;
import com.baronesa.emporio.dto.uber.UberDeliveryRequest;
import com.baronesa.emporio.dto.uber.UberDeliveryResponse;
import com.baronesa.emporio.dto.uber.UberTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UberDirectClient {

    private final UberProperties uberProperties;
    private final RestTemplate restTemplate;

    private String cachedToken;
    private Instant cachedTokenExpiry;

    public UberDeliveryResponse createDelivery(UberDeliveryRequest request) {
        String token = getAccessToken();
        String url = String.format("%s/v1/customers/%s/deliveries", uberProperties.getApiBaseUrl(), uberProperties.getCustomerId());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UberDeliveryRequest> entity = new HttpEntity<>(request, headers);

        try {
            return restTemplate.postForObject(url, entity, UberDeliveryResponse.class);
        } catch (HttpClientErrorException e) {
            log.warn("Uber create delivery error status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    private synchronized String getAccessToken() {
        if (uberProperties.getAccessToken() != null && !uberProperties.getAccessToken().isBlank()) {
            return uberProperties.getAccessToken();
        }

        if (cachedToken != null && cachedTokenExpiry != null && cachedTokenExpiry.isAfter(Instant.now().plusSeconds(30))) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", uberProperties.getClientId());
        form.add("client_secret", uberProperties.getClientSecret());
        form.add("grant_type", "client_credentials");
        form.add("scope", uberProperties.getScope());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        UberTokenResponse tokenResponse = restTemplate.postForObject(
                uberProperties.getTokenUrl(),
                entity,
                UberTokenResponse.class
        );

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new IllegalStateException("Falha ao obter token Uber");
        }

        cachedToken = tokenResponse.getAccessToken();
        long expiresIn = tokenResponse.getExpiresIn() == null ? 0 : tokenResponse.getExpiresIn();
        cachedTokenExpiry = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }
}
