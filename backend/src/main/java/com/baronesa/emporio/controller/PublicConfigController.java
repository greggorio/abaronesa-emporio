package com.baronesa.emporio.controller;

import com.baronesa.emporio.util.ConfigManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/config")
public class PublicConfigController {

    @Value("${ecommerce.app.base-url:http://localhost:5173}")
    private String ecommerceBaseUrl;

    private final ConfigManager configManager;

    public PublicConfigController(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPublicConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("ecommerceBaseUrl", ecommerceBaseUrl);
        // Expor nome do app e segmento para páginas públicas (ex.: tela de login)
        config.put("appName", configManager.getConfig("app_name", "CafeTech"));
        config.put("segmento", configManager.getConfig("segmento", "cafeteria"));
        config.put("site_cardapio_solicita_cpf", configManager.getBooleanConfig("site_cardapio_solicita_cpf", false));
        config.put("site_cardapio_solicita_telefone", configManager.getBooleanConfig("site_cardapio_solicita_telefone", false));
        return ResponseEntity.ok(config);
    }
}
