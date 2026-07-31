package com.baronesa.emporio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/produtos/**")
                .addResourceLocations("file:uploads/produtos/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/media/certificados/**")
                .addResourceLocations("file:uploads/certificados/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/signage/**")
                .addResourceLocations("file:uploads/signage/")
                .setCachePeriod(3600);
    }
}
