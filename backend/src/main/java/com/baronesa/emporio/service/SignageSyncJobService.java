package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.ProductSignage;
import com.baronesa.emporio.repository.ProductSignageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Job periódico para sincronização de produtos com signage-api
 * Garante consistência entre ERP e signage
 * Roda a cada 5 minutos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignageSyncJobService {

    private final ProductSignageRepository productSignageRepository;
    private final SignageSyncService signageSyncService;

    /**
     * Job que roda a cada 5 minutos
     * Sincroniza todos os produtos habilitados com vídeo gerado
     * Remove do signage produtos que não estão mais habilitados
     */
    @Scheduled(fixedRate = 300000) // 5 minutos = 300000 ms
    public void syncProductsJob() {
        log.info("Iniciando job de sincronização de produtos com signage...");
        
        try {
            // Busca todos os produtos habilitados com vídeo
            List<ProductSignage> enabledProducts = productSignageRepository.findEnabledWithVideo();
            
            if (enabledProducts.isEmpty()) {
                log.info("Nenhum produto habilitado com vídeo encontrado.");
                return;
            }
            
            log.info("Encontrados {} produtos para sincronizar.", enabledProducts.size());
            
            // Sincroniza todos os produtos
            int syncedCount = signageSyncService.syncAll(enabledProducts);
            
            // Limpa produtos que não estão mais na lista (desabilitados ou sem vídeo)
            boolean cleanupSuccess = signageSyncService.cleanupDisabledProducts(enabledProducts);
            
            if (cleanupSuccess) {
                log.info("Job de sincronização concluído. {}/{} produtos sincronizados.", 
                        syncedCount, enabledProducts.size());
            } else {
                log.warn("Job de sincronização concluído com falha na limpeza. {}/{} produtos sincronizados.", 
                        syncedCount, enabledProducts.size());
            }
            
        } catch (Exception e) {
            log.error("Erro no job de sincronização de produtos: {}", e.getMessage(), e);
        }
    }
}
