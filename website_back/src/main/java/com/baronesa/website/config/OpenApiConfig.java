package com.baronesa.website.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8085}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Villa Custom Viking Pub - API")
                        .version("1.0.0")
                        .description("API REST para gerenciamento de eventos, quiz e sistema de reservas do Villa Custom Viking Pub")
                        .contact(new Contact()
                                .name("Villa Custom")
                                .url("https://villacustom.com.br")
                                .email("contato@villacustom.com.br"))
                        .license(new License()
                                .name("Proprietário")
                                .url("https://villacustom.com.br")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Servidor Local - Desenvolvimento"),
                        new Server()
                                .url("https://api.villacustom.com.br")
                                .description("Servidor de Produção")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Autenticação JWT. Token gerado pelo sistema ERP compartilhado.")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
