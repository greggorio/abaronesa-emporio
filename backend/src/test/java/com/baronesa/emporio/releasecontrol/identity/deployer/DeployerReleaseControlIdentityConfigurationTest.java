package com.baronesa.emporio.releasecontrol.identity.deployer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Field;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes estruturais do pacote deployer.
 * Verificam a presença de classes, anotações, e constantes.
 */
class DeployerReleaseControlIdentityConfigurationTest {

    @Test
    void testConfigurationIsConditionalOnProperty() {
        // Classe só é instanciada quando habilitada
        ConditionalOnProperty annotation =
            DeployerReleaseControlIdentityConfiguration.class
                .getAnnotation(ConditionalOnProperty.class);
        assertNotNull(annotation, "Configuration deve ter @ConditionalOnProperty");
        assertEquals(1, annotation.name().length);
        assertEquals(
            "app.release-control.deployer-identity.enabled",
            annotation.name()[0],
            "Property must be deployer-identity.enabled"
        );
        assertEquals("true", annotation.havingValue());
    }

    @Test
    void testServiceIsConditionalOnProperty() {
        // Service também só é instanciado quando habilitado
        ConditionalOnProperty annotation =
            DeployerReleaseControlIdentityService.class
                .getAnnotation(ConditionalOnProperty.class);
        assertNotNull(annotation, "Service deve ter @ConditionalOnProperty");
        assertEquals(1, annotation.name().length);
        assertEquals(
            "app.release-control.deployer-identity.enabled",
            annotation.name()[0]
        );
    }

    @Test
    void testControllerIsConditionalOnProperty() {
        // Controller também só é instanciado quando habilitado
        ConditionalOnProperty annotation =
            DeployerReleaseControlIdentityController.class
                .getAnnotation(ConditionalOnProperty.class);
        assertNotNull(annotation, "Controller deve ter @ConditionalOnProperty");
        assertEquals(1, annotation.name().length);
        assertEquals(
            "app.release-control.deployer-identity.enabled",
            annotation.name()[0]
        );
    }

    @Test
    void testServiceAudienceConstant() throws Exception {
        // Audience deve ser constante do deployer
        Field audienceField = DeployerReleaseControlIdentityService.class
            .getDeclaredField("AUDIENCE");
        audienceField.setAccessible(true);
        String audience = (String) audienceField.get(null);
        assertEquals("emporio-release-control-deployer", audience);
    }

    @Test
    void testServiceScopeConstant() throws Exception {
        // Scope deve ser do deployer, nunca publisher
        Field scopeField = DeployerReleaseControlIdentityService.class
            .getDeclaredField("SCOPE");
        scopeField.setAccessible(true);
        String scope = (String) scopeField.get(null);
        assertEquals("deployment:read deployment:execute deployment:rollback", scope);
        assertFalse(scope.contains("release:"), "scope must not contain release:");
        assertTrue(scope.endsWith("deployment:rollback"), "scope must contain rollback last");
    }

    @Test
    void testServiceTtlConstant() throws Exception {
        // TTL deve ser 300 segundos
        Field ttlField = DeployerReleaseControlIdentityService.class
            .getDeclaredField("TTL_SECONDS");
        ttlField.setAccessible(true);
        long ttl = (long) ttlField.get(null);
        assertEquals(300L, ttl);
    }

    @Test
    void testKeyMaterialIsRecord() {
        // KeyMaterial deve ser um record (dados simples)
        assertTrue(
            DeployerReleaseControlIdentityKeyMaterial.class.isRecord(),
            "KeyMaterial deve ser um record"
        );
    }

    @Test
    void testKeyMaterialComponentTypes() throws Exception {
        // Record deve ter components: issuer, keyId, privateKey, publicKey
        assertTrue(
            DeployerReleaseControlIdentityKeyMaterial.class.getDeclaredConstructor(
                String.class, String.class, RSAPrivateCrtKey.class, RSAPublicKey.class
            ) != null,
            "KeyMaterial deve ter constructor com os 4 componentes"
        );
    }

    @Test
    void testPublisherIdentityNotTouched() throws Exception {
        // Verifica que classe publisher não foi modificada
        org.springframework.security.access.prepost.PreAuthorize publisherAnnotation =
            com.baronesa.emporio.releasecontrol.identity.ReleaseControlIdentityController.class
                .getMethod("token", jakarta.servlet.http.HttpServletRequest.class,
                           org.springframework.security.core.Authentication.class)
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
        assertNotNull(publisherAnnotation, "Publisher controller must still have @PreAuthorize");
    }
}
