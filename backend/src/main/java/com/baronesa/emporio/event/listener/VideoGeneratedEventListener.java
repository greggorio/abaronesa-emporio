package com.baronesa.emporio.event.listener;

import com.baronesa.emporio.entity.ProductSignage;
import com.baronesa.emporio.event.VideoGeneratedEvent;
import com.baronesa.emporio.service.SignageSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listener para VideoGeneratedEvent
 * Dispara a sincronização automática com signage-api quando um vídeo é gerado
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VideoGeneratedEventListener {

    private final SignageSyncService signageSyncService;

    @EventListener
    public void handleVideoGenerated(VideoGeneratedEvent event) {
        ProductSignage productSignage = event.getProductSignage();
        
        log.info("Vídeo gerado para produto {}. Iniciando sincronização com signage...",
                productSignage.getProduto().getId());
        
        try {
            boolean success = signageSyncService.syncProduct(productSignage);
            
            if (success) {
                log.info("Produto {} sincronizado com sucesso no signage.",
                        productSignage.getProduto().getId());
            } else {
                log.warn("Falha na sincronização do produto {}. Será tentado novamente pelo job.",
                        productSignage.getProduto().getId());
            }
        } catch (Exception e) {
            log.error("Erro ao processar VideoGeneratedEvent para produto {}: {}",
                    productSignage.getProduto().getId(), e.getMessage(), e);
        }
    }
}
