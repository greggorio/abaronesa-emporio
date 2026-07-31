package com.baronesa.emporio.releasecontrol.identity.deployer;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes estruturais de rotas e autorização do deployer.
 */
class DeployerReleaseControlIdentityHttpSecurityTest {

    @Test
    void testControllerHasCorrectBaseMapping() {
        // Controller deve estar mapeado em /api/release-control/identity/deployer
        RequestMapping mapping =
            DeployerReleaseControlIdentityController.class
                .getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertEquals(1, mapping.value().length);
        assertEquals("/api/release-control/identity/deployer", mapping.value()[0]);
    }

    @Test
    void testJwksMethodExists() throws Exception {
        // Deve existir método jwks com @GetMapping("/jwks")
        var method = DeployerReleaseControlIdentityController.class
            .getMethod("jwks");
        assertNotNull(method);
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(getMapping);
        assertEquals(1, getMapping.value().length);
        assertEquals("/jwks", getMapping.value()[0]);
    }

    @Test
    void testTokenMethodExists() throws Exception {
        // Deve existir método token com @PostMapping("/token")
        var method = DeployerReleaseControlIdentityController.class
            .getMethod("token", jakarta.servlet.http.HttpServletRequest.class,
                       org.springframework.security.core.Authentication.class);
        assertNotNull(method);
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertEquals(1, postMapping.value().length);
        assertEquals("/token", postMapping.value()[0]);
    }

    @Test
    void testTokenMethodHasPreAuthorize() throws Exception {
        // Método token deve ter @PreAuthorize("hasRole('SYSTEM')")
        var method = DeployerReleaseControlIdentityController.class
            .getMethod("token", jakarta.servlet.http.HttpServletRequest.class,
                       org.springframework.security.core.Authentication.class);
        PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
        assertNotNull(auth, "token method must have @PreAuthorize");
        assertTrue(
            auth.value().contains("SYSTEM"),
            "@PreAuthorize must check for SYSTEM role"
        );
    }

    @Test
    void testJwksResponseIsRecord() {
        // Resposta JWKS deve ser um record com fields públicos
        assertTrue(
            DeployerReleaseControlIdentityController.JwksResponse.class.isRecord(),
            "JwksResponse deve ser um record"
        );
    }

    @Test
    void testJwkKeyResponseIsRecord() {
        // Resposta JWK deve conter kty, use, alg, kid, n, e
        assertTrue(
            DeployerReleaseControlIdentityController.JwkKey.class.isRecord(),
            "JwkKey deve ser um record"
        );
        // Record tem 6 componentes: kty, use, alg, kid, n, e
        assertEquals(6, DeployerReleaseControlIdentityController.JwkKey.class
            .getRecordComponents().length);
    }

    @Test
    void testTokenResponseIsRecord() {
        // Resposta token deve conter accessToken, tokenType, expiresIn, scope
        assertTrue(
            DeployerReleaseControlIdentityController.TokenResponse.class.isRecord(),
            "TokenResponse deve ser um record"
        );
        assertEquals(4, DeployerReleaseControlIdentityController.TokenResponse.class
            .getRecordComponents().length);
    }

    @Test
    void testTokenResponseExpiresInType() {
        // expiresIn deve ser long (não String)
        var component = java.util.Arrays.stream(
            DeployerReleaseControlIdentityController.TokenResponse.class.getRecordComponents()
        ).filter(c -> c.getName().equals("expiresIn")).findFirst();
        assertTrue(component.isPresent());
        assertEquals(long.class, component.get().getType());
    }

    @Test
    void testRestControllerAnnotation() {
        // Deve ser @RestController
        assertTrue(
            DeployerReleaseControlIdentityController.class
                .isAnnotationPresent(RestController.class),
            "Controller deve ser @RestController"
        );
    }
}
