package com.baronesa.emporio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/** Liga o filtro de log que registra método, URI e informações básicas de cada requisição HTTP. */
@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(false);
        filter.setIncludePayload(true);
        filter.setBeforeMessagePrefix("Incoming request: ");
        filter.setAfterMessagePrefix("Completed request: ");
        filter.setAfterMessageSuffix("");
        filter.setBeforeMessageSuffix("");
        filter.setMaxPayloadLength(10000);
        return filter;
    }
}
