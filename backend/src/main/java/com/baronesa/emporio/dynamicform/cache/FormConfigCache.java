package com.baronesa.emporio.dynamicform.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.baronesa.emporio.config.form.base.BaseFormConfig;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class FormConfigCache {

    private final Cache<String, BaseFormConfig> cache;

    public FormConfigCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
    }

    public BaseFormConfig get(String entityType) {
        return cache.getIfPresent(entityType);
    }

    public void put(String entityType, BaseFormConfig config) {
        cache.put(entityType, config);
    }

    public void invalidate(String entityType) {
        cache.invalidate(entityType);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}