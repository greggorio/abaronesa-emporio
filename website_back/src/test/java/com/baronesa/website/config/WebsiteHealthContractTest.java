package com.baronesa.website.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsiteHealthContractTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void productionExposesOnlySanitizedHealth() throws IOException {
        String properties = Files.readString(
                ROOT.resolve("src/main/resources/application-prod.properties"));

        assertTrue(properties.contains("management.endpoints.web.exposure.include=health"));
        assertTrue(properties.contains("management.endpoint.health.show-details=never"));
        assertTrue(properties.contains("management.endpoint.health.show-components=never"));
        assertFalse(properties.contains("env,"));
        assertFalse(properties.contains("heapdump"));
        assertFalse(properties.contains("configprops"));
    }

    @Test
    void securityAllowsDedicatedHealthWithoutJwt() throws IOException {
        String security = Files.readString(
                ROOT.resolve("src/main/java/com/baronesa/website/config/SecurityConfig.java"));

        assertTrue(security.contains(
                ".requestMatchers(\"/actuator/health\").permitAll()"));
        assertFalse(security.contains(
                ".requestMatchers(\"/actuator/**\").permitAll()"));
    }
}
