package com.baronesa.website.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ErpConfig {

    @Value("${erp.api.url}")
    private String apiUrl;

    public String getApiUrl() {
        return apiUrl;
    }
}
