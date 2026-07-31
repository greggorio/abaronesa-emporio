package com.baronesa.emporio.cache;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class PermissaoCache {

    private static final long EXPIRACAO_MILIS = 10 * 60 * 1000; // 10 minutos

    private final Map<String, ValorCache<Boolean>> cache = new ConcurrentHashMap<>();

    public boolean get(String key, Supplier<Boolean> loader) {
        ValorCache<Boolean> valor = cache.get(key);

        if (valor == null || valor.expirou()) {
            boolean novoValor = loader.get();
            cache.put(key, new ValorCache<>(novoValor));
            return novoValor;
        }
        return valor.valor();
    }

    public void invalidateAll() {
        cache.clear();
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    private record ValorCache<T>(T valor, long criadoEm) {
        ValorCache(T valor) {
            this(valor, Instant.now().toEpochMilli());
        }
        boolean expirou() {
            return (Instant.now().toEpochMilli() - criadoEm) > EXPIRACAO_MILIS;
        }
    }
}
