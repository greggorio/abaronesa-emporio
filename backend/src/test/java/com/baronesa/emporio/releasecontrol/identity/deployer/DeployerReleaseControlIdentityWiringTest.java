package com.baronesa.emporio.releasecontrol.identity.deployer;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * O pacote já tinha testes estruturais que apenas conferiam anotações, e por isso
 * um defeito de wiring chegou intacto à produção: a classe declara dois
 * construtores e nenhum estava anotado, então o Spring não conseguia escolher e
 * caía no construtor sem argumentos, que não existe. Como o bean só é criado com
 * deployer-identity.enabled=true — verdadeiro apenas em produção — nada quebrava
 * antes do deploy comercial.
 *
 * Este teste constrói o bean pelo próprio contêiner, que é a única forma de
 * exercitar a seleção de construtor.
 */
class DeployerReleaseControlIdentityWiringTest {

    private static DeployerReleaseControlIdentityKeyMaterial material() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair pair = generator.generateKeyPair();
        return new DeployerReleaseControlIdentityKeyMaterial(
                "https://erp.example.invalid/api/release-control/identity/deployer",
                "test-key-identifier-0000000000",
                (RSAPrivateCrtKey) pair.getPrivate(),
                (RSAPublicKey) pair.getPublic()
        );
    }

    @Test
    void springCanConstructTheServiceFromItsKeyMaterial() throws Exception {
        DeployerReleaseControlIdentityKeyMaterial keyMaterial = material();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getSystemProperties()
                    .put("app.release-control.deployer-identity.enabled", "true");
            context.registerBean(
                    DeployerReleaseControlIdentityKeyMaterial.class, () -> keyMaterial
            );
            context.register(DeployerReleaseControlIdentityService.class);
            context.refresh();

            DeployerReleaseControlIdentityService service =
                    context.getBean(DeployerReleaseControlIdentityService.class);
            assertNotNull(service, "o contêiner deve conseguir instanciar o serviço");
        }
    }

    @Test
    void theControllerResolvesTheServiceThroughTheContainer() throws Exception {
        DeployerReleaseControlIdentityKeyMaterial keyMaterial = material();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getSystemProperties()
                    .put("app.release-control.deployer-identity.enabled", "true");
            context.registerBean(
                    DeployerReleaseControlIdentityKeyMaterial.class, () -> keyMaterial
            );
            context.register(DeployerReleaseControlIdentityService.class);
            context.register(DeployerReleaseControlIdentityController.class);
            context.refresh();

            assertNotNull(context.getBean(DeployerReleaseControlIdentityController.class));
            assertSame(
                    keyMaterial,
                    context.getBean(DeployerReleaseControlIdentityService.class).keyMaterial(),
                    "o serviço deve receber exatamente o material registrado"
            );
        }
    }
}
