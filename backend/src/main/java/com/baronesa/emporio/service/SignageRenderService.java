package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.SignageRenderRequestDTO;
import com.baronesa.emporio.dto.SignageRenderResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignageRenderService {

    private final RestTemplate restTemplate;

    @Value("${signage.render.base-url:http://localhost:4010}")
    private String renderBaseUrl;

    public SignageRenderResponseDTO renderHtml(SignageRenderRequestDTO request) {
        String endpoint = normalizeBaseUrl(renderBaseUrl) + "/render/html";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            HttpEntity<SignageRenderRequestDTO> entity = new HttpEntity<>(request, headers);
            var responseEntity = restTemplate.postForEntity(endpoint, entity, SignageRenderResponseDTO.class);
            SignageRenderResponseDTO response = responseEntity.getBody();
            if (response == null) {
                throw new IllegalStateException("Resposta vazia do signage-render");
            }
            response.setUrl(resolveUrl(response.getUrl()));
            return response;
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.error("Erro do signage-render (status {}): {}", e.getRawStatusCode(), body);
            throw e;
        } catch (RestClientException e) {
            log.error("Falha ao chamar signage-render: {}", e.getMessage());
            throw e;
        }
    }

    private String normalizeBaseUrl(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String resolveUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (!url.startsWith("/")) {
            return normalizeBaseUrl(renderBaseUrl) + "/" + url;
        }
        return normalizeBaseUrl(renderBaseUrl) + url;
    }
}
