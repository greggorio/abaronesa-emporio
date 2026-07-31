package com.baronesa.emporio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.dto.openai.OpenAiConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiImageClient {

    private final OpenAiConfigService openAiConfigService;
    
    @Value("${signage.ai.model:gpt-image-1}")
    private String model;
    @Value("${store.upload.produto-dir:uploads/produtos}")
    private String produtoUploadDir;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/images/edits";
    
    /**
     * Gera imagem editada a partir de imagem original
     * 
     * @param originalImageUrl URL da imagem original do produto
     * @param prompt Instruções/prompt para edição
     * @param size Tamanho da saída (ex: 1024x1024)
     * @return bytes da imagem PNG gerada
     */
    public byte[] generateImage(String originalImageUrl, String prompt, String size) {
        OpenAiConfigDTO config = openAiConfigService.getConfig();
        if (!config.getHabilitado() || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("OpenAI para imagens não está habilitada ou não possui API key");
        }

        try {
            log.info("Chamando GPT Image API para gerar imagem. Model: {}, Size: {}", model, size);
            
            // Preparar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(config.getApiKey());
            
            // Preparar body (multipart/form-data)
            byte[] imageBytes = downloadImage(originalImageUrl);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", model);
            body.add("prompt", prompt);
            body.add("size", size);
            body.add("n", 1); // número de imagens
            body.add("image", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "image.png";
                }
            });
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                OPENAI_API_URL,
                requestEntity,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return extractImageBytes(response.getBody());
            } else {
                throw new RuntimeException("Erro na API GPT Image: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Erro ao chamar GPT Image API", e);
            throw new RuntimeException("Falha na geração de imagem IA", e);
        }
    }
    
    /**
     * Extrai bytes da imagem da resposta da API
     */
    private byte[] extractImageBytes(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.path("data");
        
        if (data.isArray() && data.size() > 0) {
            String b64Image = data.get(0).path("b64_json").asText();
            return Base64.getDecoder().decode(b64Image);
        }
        
        throw new RuntimeException("Resposta da API não contém imagem");
    }

    private byte[] downloadImage(String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return response.getBody();
                }
                throw new RuntimeException("Não foi possível baixar a imagem base (" + response.getStatusCode() + ")");
            }

            String relative = url.startsWith("/") ? url.substring(1) : url;
            String normalized = relative;
            String[] prefixes = {"uploads/produtos/", "media/produtos/", "produtos/"};
            for (String prefix : prefixes) {
                if (normalized.startsWith(prefix)) {
                    normalized = normalized.substring(prefix.length());
                    break;
                }
            }
            Path baseDir = Paths.get(produtoUploadDir).toAbsolutePath();
            Path filePath = baseDir.resolve(normalized).normalize();
            if (!Files.exists(filePath)) {
                throw new RuntimeException("Imagem base não encontrada: " + filePath);
            }
            return Files.readAllBytes(filePath);
        } catch (Exception e) {
            throw new RuntimeException("Não foi possível baixar a imagem base", e);
        }
    }
}
