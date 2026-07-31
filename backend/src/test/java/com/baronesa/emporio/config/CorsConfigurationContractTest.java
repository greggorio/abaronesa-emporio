package com.baronesa.emporio.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorsConfigurationContractTest {
    @Test void acceptsCanonicalOrigins() {
        assertEquals(2, SecurityConfig.parseOrigins(
                "https://emporio.abaronesa.net.br,https://erp-emporio.abaronesa.net.br").size());
    }
    @Test void rejectsWildcardPathQueryEmptyAndDuplicate() {
        for (String value : new String[]{"*", "https://example.invalid/path",
                "https://example.invalid?q=x", "https://example.invalid,",
                "https://example.invalid,https://example.invalid"}) {
            assertThrows(IllegalStateException.class, () -> SecurityConfig.parseOrigins(value));
        }
    }
}
