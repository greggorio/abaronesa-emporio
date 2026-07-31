package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.ProductSignage;
import com.baronesa.emporio.repository.ProductSignageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSignageJobService {

    private final ProductSignageRepository productSignageRepository;

    private final ProductSignageAiService productSignageAiService;

    public int executar() {
        List<ProductSignage> elegiveis = productSignageRepository.findEligibleForJob();
        int processed = 0;
        for (ProductSignage signage : elegiveis) {
            var product = signage.getProduto();
            log.info("ProductSignageJobService solicitando OpenAI para produto {} ({})", product.getId(), product.getNome());
            var result = productSignageAiService.generate(product, product.getImagemPrincipal());
            if (result != null) {
                processed++;
            } else {
                log.warn("ProductSignageJobService não gerou conteúdo para product_signage {} por erro na OpenAI", product.getId());
            }
        }
        log.info("ProductSignageJobService finalizado: {}/{} produtos com OpenAI gerado", processed, elegiveis.size());
        return processed;
    }
}
