package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.SignageSyncProductRequest;
import com.baronesa.emporio.dto.SignageSyncProductResponse;
import com.baronesa.emporio.entity.ProductSignage;
import com.baronesa.emporio.repository.ProductSignageRepository;
import com.baronesa.emporio.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Serviço de sincronização de produtos com signage-api
 * Responsável por enviar/atualizar produtos no signage quando vídeos são gerados
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignageSyncService {

    private final RestTemplate restTemplate;
    private final ProductSignageRepository productSignageRepository;
    private final JwtTokenProvider jwtTokenProvider;

    // Best-effort cleanup debounce (single-instance). Avoids spamming signage-api on bursty syncs.
    private final AtomicLong lastCleanupAttemptMs = new AtomicLong(0L);

    @Value("${signage.api.url:http://localhost:4020}")
    private String signageApiUrl;

    @Value("${signage.api.enabled:true}")
    private Boolean signageApiEnabled;

    @Value("${signage.api.cleanup-on-sync:true}")
    private Boolean cleanupOnSync;

    @Value("${signage.api.cleanup-min-interval-seconds:60}")
    private Integer cleanupMinIntervalSeconds;

    private static final String SYNC_ENDPOINT = "/api/products/sync";
    private static final String CLEANUP_ENDPOINT = "/api/products/sync";

    /**
     * Sincroniza um produto específico com o signage-api
     * Chamado quando um vídeo é gerado ou atualizado
     * 
     * @param productSignage Produto a ser sincronizado
     * @return true se sincronizado com sucesso
     */
    public boolean syncProduct(ProductSignage productSignage) {
        return syncProductInternal(productSignage, true);
    }

    private boolean syncProductInternal(ProductSignage productSignage, boolean cleanupAfter) {
        if (!signageApiEnabled) {
            log.info("Signage API desabilitada. Ignorando sincronização do produto {}", 
                    productSignage.getProduto().getId());
            return false;
        }

        if (!productSignage.getEnabled() || productSignage.getMp4Url() == null) {
            log.warn("Produto {} não habilitado ou sem vídeo. Ignorando.", 
                    productSignage.getProduto().getId());
            return false;
        }

        try {
            String token = generateSystemToken();
            
            SignageSyncProductRequest request = SignageSyncProductRequest.builder()
                    .title(productSignage.getProduto().getNome())
                    .mediaUrl(productSignage.getMp4Url())
                    .renderHash(productSignage.getRenderHash())
                    .durationSeconds(productSignage.getDurationSeconds() != null ? 
                            productSignage.getDurationSeconds() : 10)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<SignageSyncProductRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<SignageSyncProductResponse> response = restTemplate.postForEntity(
                    signageApiUrl + SYNC_ENDPOINT,
                    entity,
                    SignageSyncProductResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                SignageSyncProductResponse body = response.getBody();
                
                // Atualiza metadados de sincronização
                productSignage.setSignageScreenId(body.getScreen().getId());
                productSignage.setLastSyncAt(LocalDateTime.now());
                productSignageRepository.save(productSignage);
                
                log.info("Produto {} sincronizado com sucesso. Action: {}, ScreenId: {}",
                        productSignage.getProduto().getId(),
                        body.getAction(),
                        body.getScreen().getId());

                // Remove screens antigas do signage-api para evitar duplicação na playlist e manter consistência.
                // É best-effort e debounced para evitar custo/latência excessivos.
                if (cleanupAfter) {
                    requestCleanupIfDue();
                }
                
                return true;
            } else {
                log.error("Falha ao sincronizar produto {}. Status: {}",
                        productSignage.getProduto().getId(),
                        response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Erro ao sincronizar produto {}: {}",
                    productSignage.getProduto().getId(),
                    e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sincroniza uma lista de produtos
     * Usado pelo job periódico
     * 
     * @param products Lista de produtos para sincronizar
     * @return Número de produtos sincronizados com sucesso
     */
    public int syncAll(List<ProductSignage> products) {
        if (!signageApiEnabled) {
            log.info("Signage API desabilitada. Ignorando sincronização em lote.");
            return 0;
        }

        log.info("Iniciando sincronização de {} produtos", products.size());
        
        int successCount = 0;
        for (ProductSignage product : products) {
            if (syncProductInternal(product, false)) {
                successCount++;
            }
        }
        
        log.info("Sincronização concluída. {}/{} produtos sincronizados.", 
                successCount, products.size());
        
        return successCount;
    }

    private void requestCleanupIfDue() {
        if (!Boolean.TRUE.equals(signageApiEnabled)) {
            return;
        }
        if (!Boolean.TRUE.equals(cleanupOnSync)) {
            return;
        }

        long now = System.currentTimeMillis();
        int minIntervalMs = Math.max(0, (cleanupMinIntervalSeconds == null ? 60 : cleanupMinIntervalSeconds) * 1000);
        long last = lastCleanupAttemptMs.get();
        if (minIntervalMs > 0 && now - last < minIntervalMs) {
            return;
        }
        if (!lastCleanupAttemptMs.compareAndSet(last, now)) {
            return;
        }

        try {
            List<ProductSignage> enabledProducts = productSignageRepository.findEnabledWithVideo();
            boolean ok = cleanupDisabledProducts(enabledProducts);
            if (!ok) {
                log.warn("Cleanup imediato do signage-api falhou (best-effort).");
            }
        } catch (Exception e) {
            log.warn("Erro ao solicitar cleanup imediato do signage-api: {}", e.getMessage());
        }
    }

    /**
     * Remove do signage produtos que não estão mais habilitados ou não possuem vídeo
     * Chamado pelo job periódico após syncAll
     * 
     * @param activeProducts Lista de produtos que devem permanecer no signage
     * @return true se limpeza realizada com sucesso
     */
    public boolean cleanupDisabledProducts(List<ProductSignage> activeProducts) {
        if (!signageApiEnabled) {
            return false;
        }

        try {
            String token = generateSystemToken();
            
            // Coleta todos os renderHashes dos produtos ativos
            List<String> activeHashes = activeProducts.stream()
                    .map(ProductSignage::getRenderHash)
                    .filter(hash -> hash != null && !hash.isEmpty())
                    .collect(Collectors.toList());

            if (activeHashes.isEmpty()) {
                log.warn("Nenhum renderHash ativo encontrado. Pulando limpeza.");
                return true;
            }

            // Prepara request de cleanup
            var cleanupRequest = new java.util.HashMap<String, Object>();
            cleanupRequest.put("renderHashes", activeHashes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<java.util.Map<String, Object>> entity = 
                    new HttpEntity<>(cleanupRequest, headers);

            ResponseEntity<java.util.Map> response = restTemplate.exchange(
                    signageApiUrl + CLEANUP_ENDPOINT,
                    HttpMethod.DELETE,
                    entity,
                    java.util.Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Integer removed = (Integer) response.getBody().get("removed");
                log.info("Limpeza concluída. {} produtos removidos do signage.", removed);
                return true;
            } else {
                log.error("Falha na limpeza. Status: {}", response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Erro ao limpar produtos desabilitados: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gera token JWT de sistema para autenticação no signage-api
     */
    private String generateSystemToken() {
        return jwtTokenProvider.generateToken("system", Set.of("SYSTEM"), 0L);
    }
}
