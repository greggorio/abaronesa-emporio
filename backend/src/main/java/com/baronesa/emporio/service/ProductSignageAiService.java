package com.baronesa.emporio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baronesa.emporio.dto.AiImageGenerationResponseDTO;
import com.baronesa.emporio.dto.ProductSignagePalette;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProductSignage;
import com.baronesa.emporio.entity.SignageTemplate;
import com.baronesa.emporio.enums.AiImageMode;
import com.baronesa.emporio.enums.ProductSignageStatus;
import com.baronesa.emporio.repository.ProductSignageRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import com.baronesa.emporio.repository.SignageTemplateRepository;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSignageAiService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final OpenAiConfigService openAiConfigService;
    private final ProductSignageRepository productSignageRepository;
    private final ProdutoRepository produtoRepository;
    private final SignageTemplateRepository templateRepository;
    private final AiImageHashService hashService;
    private final AiImageStorageService storageService;
    private final OpenAiImageClient openAiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record Phrases(String headline, String subtitle, String cta) {}
    public record ProductSignageAiResult(Phrases phrases, ProductSignagePalette palette) {}

    public ProductSignageAiResult generate(Produto product, String imageUrl) {
        if (!openAiConfigService.isEnabled()) {
            markStatusError(product, "OpenAI desabilitada");
            return null;
        }

        OpenAiService openAiService = openAiConfigService.createOpenAiService();
        if (openAiService == null) {
            markStatusError(product, "Não foi possível inicializar o serviço OpenAI");
            return null;
        }

        var config = openAiConfigService.getConfig();
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(config.getModel())
                .messages(Arrays.asList(
                        new ChatMessage("system", systemPrompt()),
                new ChatMessage("user", userPrompt(product, imageUrl))
                ))
                .maxTokens(config.getMaxTokens())
                .temperature(0.4)
                .build();

        String response;
        try {
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            response = result.getChoices().get(0).getMessage().getContent().trim();
        } catch (Exception e) {
            markStatusError(product, "Falha ao chamar OpenAI: " + e.getMessage());
            return null;
        }

        try {
            ObjectNode payload = (ObjectNode) objectMapper.readTree(response);
            Phrases phrases = parsePhrases(payload);
            ProductSignagePalette palette = parsePalette(payload);
            return persistSuccess(product, phrases, palette);
        } catch (Exception e) {
            markStatusError(product, "Resposta inválida da OpenAI: " + e.getMessage());
            return null;
        }
    }

    private String systemPrompt() {
        return """
                Você é um gerador de Copy+Paleta para mídias digitais.
                Responda EXCLUSIVAMENTE com um JSON válido contendo os campos: headline, subtitle (opcional), cta (opcional) e palette.
                O campo palette deve ter brandColor, accentColor, bgColor e textColor com valores hexadecimais (ex: #FFFFFF).
                Nenhum texto adicional, nem markdown, nem código.""";
    }

    private String userPrompt(Produto product, String imageUrl) {
        return String.format("""
                Gere frases curtas e uma paleta harmônica para o produto:
                Nome: %s
                Descrição: %s
                Imagem: %s
                """, nonNull(product.getNome()), nonNull(product.getDescricao()), imageUrl);
    }

    private String nonNull(String value) {
        return value != null ? value : "<sem valor>";
    }

    private Phrases parsePhrases(ObjectNode payload) {
        String headline = valueOrNull(payload, "headline");
        if (headline == null || headline.isBlank()) {
            throw new IllegalArgumentException("Headline ausente");
        }
        String subtitle = valueOrNull(payload, "subtitle");
        String cta = valueOrNull(payload, "cta");
        return new Phrases(headline, subtitle, cta);
    }

    private ProductSignagePalette parsePalette(ObjectNode payload) {
        JsonNode paletteNode = payload.get("palette");
        if (paletteNode == null || !paletteNode.isObject()) {
            throw new IllegalArgumentException("Palette ausente");
        }

        var paletteMap = objectMapper.convertValue(paletteNode, new TypeReference<Map<String, Object>>() {});
        ProductSignagePalette palette = ProductSignagePalette.fromMap(paletteMap);
        if (palette == null) {
            throw new IllegalArgumentException("Palette inválida");
        }
        var colors = java.util.Arrays.asList(
                palette.getBackground(),
                palette.getText(),
                palette.getAccent(),
                palette.getAccent2()
        );
        for (var color : colors) {
            if (color != null && !ProductSignagePalette.isValidHex(color)) {
                throw new IllegalArgumentException("Uma ou mais cores da palette não são HEX válidas");
            }
        }
        return palette;
    }

    private String valueOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText().trim() : null;
    }

    private boolean isValidHex(String color) {
        return color != null && HEX_COLOR.matcher(color).matches();
    }

    private ProductSignageAiResult persistSuccess(Produto product, Phrases phrases, ProductSignagePalette palette) {
        ProductSignage signage = findOrCreate(product);
        signage.setPhrases(serialize(phrases));
        signage.setPalette(serialize(palette));
        signage.setStatus(ProductSignageStatus.AI_GENERATED);
        signage.setLastAttemptAt(LocalDateTime.now());
        signage.setLastResultAt(LocalDateTime.now());
        productSignageRepository.save(signage);
        log.info("Paleta e copy geradas para product_signage {}", product.getId());
        return new ProductSignageAiResult(phrases, palette);
    }

    private void markStatusError(Produto product, String reason) {
        ProductSignage signage = findOrCreate(product);
        signage.setStatus(ProductSignageStatus.ERROR);
        signage.setLastAttemptAt(LocalDateTime.now());
        productSignageRepository.save(signage);
        log.error("Product signage {} marcado como ERROR: {}", product.getId(), reason);
    }

    private ProductSignage findOrCreate(Produto product) {
        return productSignageRepository.findByProdutoId(product.getId())
                .orElseGet(() -> ProductSignage.builder()
                        .produto(product)
                        .enabled(true)
                        .status(ProductSignageStatus.PENDING)
                        .build());
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Não foi possível serializar valor: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Gera imagem IA para um produto
     * 
     * @param productId ID do produto
     * @param force Se true, ignora cache e gera nova imagem
     * @return DTO com resultado da geração
     */
    @Transactional
    public AiImageGenerationResponseDTO generateAiImage(Long productId, boolean force) {
        log.info("Iniciando geração de imagem IA para produto: {}, force: {}", productId, force);
        
        // 1. Buscar produto e sua configuração de signage
        Produto produto = produtoRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + productId));
        
        ProductSignage signage = productSignageRepository.findByProdutoId(productId)
            .orElseThrow(() -> new RuntimeException("Signage não configurado para produto: " + productId));
        
        // 2. Buscar template e config de IA
        String templateId = signage.getTemplatePreference();
        SignageTemplate template = templateRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new RuntimeException("Template não encontrado: " + templateId));
        
        if (!template.getAiEnabled()) {
            throw new RuntimeException("Template não suporta geração de imagem IA: " + templateId);
        }
        
        // 3. Calcular hash da imagem original
        String originalImageHash = calculateImageHash(produto.getImagemPrincipal());
        
        // 4. Calcular hash final (inclui revision se force=true)
        int currentRevision = signage.getAiRevision() != null ? signage.getAiRevision() : 0;
        int newRevision = force ? currentRevision + 1 : currentRevision;
        
        String hash = hashService.calculateHash(
            originalImageHash,
            templateId,
            AiImageMode.valueOf(template.getAiMode()),
            template.getAiPromptVersion(),
            template.getAiOutputSize(),
            newRevision
        );
        
        // 5. Verificar cache
        if (!force && storageService.exists(hash)) {
            log.info("Imagem encontrada em cache: {}", hash);
            return buildResponse(hash, true);
        }
        
        // 6. Se force=true, deletar imagem antiga
        if (force && signage.getAiImageHash() != null) {
            try {
                storageService.deleteImage(signage.getAiImageHash());
                log.info("Imagem antiga deletada: {}", signage.getAiImageHash());
            } catch (IOException e) {
                log.warn("Não foi possível deletar imagem antiga: {}", signage.getAiImageHash(), e);
            }
        }
        
        // 7. Chamar API de IA
        try {
            byte[] imageBytes = openAiClient.generateImage(
                produto.getImagemPrincipal(),
                template.getAiPrompt(),
                template.getAiOutputSize()
            );
            
            // 8. Salvar imagem
            storageService.saveImage(imageBytes, hash);
            
            // 9. Atualizar entidade ProductSignage
            signage.setAiImageHash(hash);
            signage.setAiRevision(newRevision);
            signage.setAiGeneratedAt(LocalDateTime.now());
            productSignageRepository.save(signage);
            
            log.info("Imagem IA gerada e salva com sucesso: {}", hash);
            return buildResponse(hash, false);
            
        } catch (Exception e) {
            log.error("Erro ao gerar imagem IA para produto: {}", productId, e);
            throw new RuntimeException("Falha na geração de imagem IA", e);
        }
    }

    private String calculateImageHash(String imageUrl) {
        // Implementar cálculo de hash da imagem original (pode ser URL-based ou baixar e calcular)
        // Por simplicidade, usando hash da URL por enquanto
        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(imageUrl);
    }

    private AiImageGenerationResponseDTO buildResponse(String hash, boolean cached) {
        return AiImageGenerationResponseDTO.builder()
            .assetHash(hash)
            .assetUrl(storageService.resolveUrl(hash))
            .cached(cached)
            .generatedAt(cached ? null : LocalDateTime.now())
            .build();
    }
}
