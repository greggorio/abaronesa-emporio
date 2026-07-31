package com.baronesa.website.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${store.upload.galeria-dir:uploads/galeria}")
    private String galeriaUploadDir;

    @Value("${store.upload.theme-assets-dir:uploads/theme-assets}")
    private String themeAssetsDir;

    @Value("${store.upload.android-assets-dir:uploads/android-assets}")
    private String androidAssetsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path galeriaPath = Paths.get(galeriaUploadDir).toAbsolutePath().normalize();
        String galeriaPathUri = galeriaPath.toUri().toString();

        Path themeAssetsPath = Paths.get(themeAssetsDir).toAbsolutePath().normalize();
        String themeAssetsPathUri = themeAssetsPath.toUri().toString();

        Path androidAssetsPath = Paths.get(androidAssetsDir).toAbsolutePath().normalize();
        String androidAssetsPathUri = androidAssetsPath.toUri().toString();

        registry.addResourceHandler("/media/galeria/**")
                .addResourceLocations(galeriaPathUri);

        registry.addResourceHandler("/media/theme-assets/**")
                .addResourceLocations("file:" + themeAssetsPath.toString() + "/");

        registry.addResourceHandler("/media/android-assets/**")
                .addResourceLocations("file:" + androidAssetsPath.toString() + "/");
    }
}
