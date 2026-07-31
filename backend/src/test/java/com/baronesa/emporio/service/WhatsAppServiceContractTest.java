package com.baronesa.emporio.service;

import com.baronesa.emporio.util.ConfigManager;
import org.junit.jupiter.api.Test;
import java.net.http.HttpClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppServiceContractTest {
    @Test void blankPersistedValueFallsBackToRuntime() {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getConfig("whatsapp_service_url", "")).thenReturn(" ");
        var service = new WhatsAppService(config, "http://whatsapp_service:3001", HttpClient.newHttpClient());
        assertEquals("http://whatsapp_service:3001/status", service.serviceUri("/status").toString());
    }
    @Test void persistedValueHasPrecedence() {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getConfig("whatsapp_service_url", "")).thenReturn("https://localhost:9443/");
        var service = new WhatsAppService(config, "http://whatsapp_service:3001", HttpClient.newHttpClient());
        assertEquals("https://localhost:9443/status", service.serviceUri("/status").toString());
    }
    @Test void rejectsUnsafeUrlsBeforeRequest() {
        for (String url : new String[]{"", "ftp://host", "http://user@host", "http://host?q=x", "http://host/#x"}) {
            ConfigManager config = mock(ConfigManager.class);
            when(config.getConfig("whatsapp_service_url", "")).thenReturn("");
            var service = new WhatsAppService(config, url, HttpClient.newHttpClient());
            assertThrows(IllegalStateException.class, () -> service.serviceUri("/status"));
        }
    }
}
