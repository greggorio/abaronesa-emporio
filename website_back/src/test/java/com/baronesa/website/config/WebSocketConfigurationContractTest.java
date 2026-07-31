package com.baronesa.website.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WebSocketConfigurationContractTest {
    @Test void websocketUsesSameValidatedOriginParser() {
        assertEquals("https://emporio.abaronesa.net.br",
                CorsConfig.parseOrigins("https://emporio.abaronesa.net.br").getFirst());
        assertThrows(IllegalStateException.class, () -> CorsConfig.parseOrigins("*"));
    }
}
