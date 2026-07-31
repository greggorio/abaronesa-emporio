package com.baronesa.website.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorsConfigurationContractTest {
    @Test void acceptsCanonicalOriginsForCorsAndWebsocket() {
        assertEquals(2, CorsConfig.parseOrigins(
                "https://emporio.abaronesa.net.br,https://erp-emporio.abaronesa.net.br").size());
    }
    @Test void rejectsUnsafeOrigins() {
        for (String value : new String[]{"*", "https://example.invalid/path",
                "https://example.invalid?q=x", "https://example.invalid,",
                "https://example.invalid,https://example.invalid"}) {
            assertThrows(IllegalStateException.class, () -> CorsConfig.parseOrigins(value));
        }
    }
}
