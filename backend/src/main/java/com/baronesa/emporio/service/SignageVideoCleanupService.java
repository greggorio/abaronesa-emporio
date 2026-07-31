package com.baronesa.emporio.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignageVideoCleanupService {

    private final SignageVideoStorageService signageVideoStorageService;

    @Value("${signage.video.retention.per-product:2}")
    private Integer retentionPerProduct;

    public int cleanupOldVideos() {
        int keep = retentionPerProduct != null ? retentionPerProduct : 2;
        if (keep < 1) keep = 1;
        return signageVideoStorageService.pruneAllProducts(keep);
    }
}

