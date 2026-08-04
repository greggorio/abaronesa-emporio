package com.baronesa.emporio.releasecontrol.identity.deployer;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prova de isolamento: audiences, scopes e rotas do publisher e deployer
 * são distintos por leitura direta do código de produção, não por literais
 * no teste.
 */
class DeployerPublisherIsolationTest {

    @Test
    void testAudiencesAreDistinctAndFromConstants() throws Exception {
        // Lê audiences reais das constantes das duas classes
        Field publisherAudField = com.baronesa.emporio.releasecontrol.identity.ReleaseControlIdentityService.class
            .getDeclaredField("AUDIENCE");
        publisherAudField.setAccessible(true);
        String publisherAud = (String) publisherAudField.get(null);

        Field deployerAudField = DeployerReleaseControlIdentityService.class
            .getDeclaredField("AUDIENCE");
        deployerAudField.setAccessible(true);
        String deployerAud = (String) deployerAudField.get(null);

        // Prova que são distintos e lidos da classe, não digitados no teste
        assertNotEquals(publisherAud, deployerAud,
            "Publisher e deployer devem ter audiences distintas");
        assertTrue(publisherAud.startsWith("emporio-release-control"));
        assertTrue(deployerAud.startsWith(publisherAud + "-"));
        assertTrue(deployerAud.endsWith("-deployer"));
    }

    @Test
    void testScopesAreDistinctAndFromConstants() throws Exception {
        // Lê scopes reais das constantes das duas classes
        Field publisherScopeField = com.baronesa.emporio.releasecontrol.identity.ReleaseControlIdentityService.class
            .getDeclaredField("SCOPE");
        publisherScopeField.setAccessible(true);
        String publisherScope = (String) publisherScopeField.get(null);

        Field deployerScopeField = DeployerReleaseControlIdentityService.class
            .getDeclaredField("SCOPE");
        deployerScopeField.setAccessible(true);
        String deployerScope = (String) deployerScopeField.get(null);

        // Prova que são distintos
        assertNotEquals(publisherScope, deployerScope,
            "Publisher e deployer devem ter scopes distintos");

        // Publisher nunca contém deployment:
        assertFalse(publisherScope.contains("deployment:"),
            "Publisher scope deve conter release:, não deployment:");
        assertTrue(publisherScope.contains("release:"));

        // Deployer nunca contém release:
        assertFalse(deployerScope.contains("release:"),
            "Deployer scope deve conter deployment:, não release:");
        assertTrue(deployerScope.contains("deployment:"));

        // Somente o deployer contém rollback; publisher permanece isolado
        assertFalse(publisherScope.contains("rollback"));
        assertEquals(
            "deployment:read deployment:execute deployment:rollback",
            deployerScope
        );
    }

    @Test
    void testRoutesAreDistinctAndFromAnnotations() throws Exception {
        // Lê @RequestMapping real do publisher
        RequestMapping publisherMapping = com.baronesa.emporio.releasecontrol.identity.ReleaseControlIdentityController.class
            .getAnnotation(RequestMapping.class);
        assertNotNull(publisherMapping);
        String publisherPath = publisherMapping.value()[0];

        // Lê @RequestMapping real do deployer
        RequestMapping deployerMapping = DeployerReleaseControlIdentityController.class
            .getAnnotation(RequestMapping.class);
        assertNotNull(deployerMapping);
        String deployerPath = deployerMapping.value()[0];

        // Prova que são distintos
        assertNotEquals(publisherPath, deployerPath,
            "Publisher e deployer devem ter rotas base distintas");

        // Verificar que o deployer contém /deployer e publisher não
        assertTrue(deployerPath.contains("/deployer"),
            "Rota deployer deve conter /deployer");
        assertFalse(publisherPath.contains("/deployer"),
            "Rota publisher não deve conter /deployer");

        assertTrue(deployerPath.startsWith(publisherPath + "/deployer"));
    }

    @Test
    void testKeyMaterialTypesAreSeparate() throws Exception {
        // KeyMaterial classes são records completamente separados
        assertTrue(DeployerReleaseControlIdentityKeyMaterial.class.isRecord());
        assertTrue(com.baronesa.emporio.releasecontrol.identity.ReleaseControlIdentityKeyMaterial.class.isRecord());

        // Têm nomes distintos que refletem seu propósito
        assertNotEquals(
            DeployerReleaseControlIdentityKeyMaterial.class.getSimpleName(),
            com.baronesa.emporio.releasecontrol.identity.ReleaseControlIdentityKeyMaterial.class.getSimpleName()
        );
    }

    @Test
    void testTTLValuesAreIdenticalByDesign() throws Exception {
        // TTL deve ser 300 segundos em ambos
        Field publisherTtlField = com.baronesa.emporio.releasecontrol.identity.ReleaseControlIdentityService.class
            .getDeclaredField("TTL_SECONDS");
        publisherTtlField.setAccessible(true);
        long publisherTtl = (long) publisherTtlField.get(null);

        Field deployerTtlField = DeployerReleaseControlIdentityService.class
            .getDeclaredField("TTL_SECONDS");
        deployerTtlField.setAccessible(true);
        long deployerTtl = (long) deployerTtlField.get(null);

        assertEquals(300L, publisherTtl);
        assertEquals(300L, deployerTtl);
        assertEquals(publisherTtl, deployerTtl,
            "TTL deve ser idêntico entre publisher e deployer");
    }
}
