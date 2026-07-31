package com.baronesa.emporio.releasecontrol.identity;

import com.baronesa.emporio.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReleaseControlIdentityContractTest {

    private static final String ISSUER =
            "http://127.0.0.1:8080/api/release-control/identity";
    private static final String KID = "publisher-test-key-0001";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static Path keyPath;
    private static KeyPair keyPair;

    @BeforeAll
    static void createKey(@TempDir Path tempDir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        keyPair = generator.generateKeyPair();
        keyPath = tempDir.resolve("issuer.pem");
        writePkcs8(keyPath, keyPair);
    }

    @AfterAll
    static void clearReferences() {
        keyPath = null;
        keyPair = null;
    }

    @Test
    void disabledConfigurationCreatesNoOperationalBridgeBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        ReleaseControlIdentityConfiguration.class,
                        ReleaseControlIdentityService.class,
                        ReleaseControlIdentityController.class
                )
                .withPropertyValues("app.release-control.identity.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReleaseControlIdentityKeyMaterial.class);
                    assertThat(context).doesNotHaveBean(ReleaseControlIdentityService.class);
                    assertThat(context).doesNotHaveBean(ReleaseControlIdentityController.class);
                });
    }

    @Test
    void acceptsHttpsAndLoopbackHttpIssuerWithValidRsa3072() {
        assertThat(load(ISSUER, keyPath, KID).publicKey().getModulus().bitLength())
                .isEqualTo(3072);
        assertThat(load("https://issuer.invalid/release-control", keyPath, KID).issuer())
                .isEqualTo("https://issuer.invalid/release-control");
    }

    @Test
    void rejectsIssuerAndKeyIdMutants() {
        for (String issuer : List.of(
                "http://issuer.invalid/identity",
                "https://user@issuer.invalid/identity",
                "https://issuer.invalid/identity/",
                "https://issuer.invalid/identity?query=1",
                "relative"
        )) {
            assertThatThrownBy(() -> load(issuer, keyPath, KID))
                    .isInstanceOf(IllegalStateException.class);
        }
        for (String kid : List.of("", "short", "-publisher-test-key", "x".repeat(65))) {
            assertThatThrownBy(() -> load(ISSUER, keyPath, kid))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsPathAndPemMutants(@TempDir Path tempDir) throws Exception {
        assertThatThrownBy(() -> load(ISSUER, Path.of("relative.pem"), KID))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> load(ISSUER, tempDir.resolve("missing.pem"), KID))
                .isInstanceOf(IllegalStateException.class);

        Path symlink = tempDir.resolve("link.pem");
        Files.createSymbolicLink(symlink, keyPath);
        assertThatThrownBy(() -> load(ISSUER, symlink, KID))
                .isInstanceOf(IllegalStateException.class);

        Path large = tempDir.resolve("large.pem");
        Files.write(large, new byte[ReleaseControlIdentityConfiguration.MAX_PRIVATE_KEY_BYTES + 1]);
        assertThatThrownBy(() -> load(ISSUER, large, KID))
                .isInstanceOf(IllegalStateException.class);

        for (String pem : List.of(
                "not a pem",
                "-----BEGIN RSA PRIVATE KEY-----\nAA==\n-----END RSA PRIVATE KEY-----",
                "-----BEGIN ENCRYPTED PRIVATE KEY-----\nAA==\n-----END ENCRYPTED PRIVATE KEY-----"
        )) {
            Path invalid = Files.createTempFile(tempDir, "invalid-", ".pem");
            Files.writeString(invalid, pem);
            assertThatThrownBy(() -> load(ISSUER, invalid, KID))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsNonRsaAndRsa2048(@TempDir Path tempDir) throws Exception {
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(256);
        Path ecPath = tempDir.resolve("ec.pem");
        writePkcs8(ecPath, ec.generateKeyPair());
        assertThatThrownBy(() -> load(ISSUER, ecPath, KID))
                .isInstanceOf(IllegalStateException.class);

        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        Path weak = tempDir.resolve("weak.pem");
        writePkcs8(weak, rsa.generateKeyPair());
        assertThatThrownBy(() -> load(ISSUER, weak, KID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwksAndTokenHaveExactPublicContract() throws Exception {
        ReleaseControlIdentityKeyMaterial material = load(ISSUER, keyPath, KID);
        ReleaseControlIdentityService service = new ReleaseControlIdentityService(
                material,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ReleaseControlIdentityController controller =
                new ReleaseControlIdentityController(service);

        var response = controller.jwks();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isNotNull();
        var keys = response.getBody().keys();
        assertThat(keys).hasSize(1);
        var jwk = keys.getFirst();
        assertThat(jwk.kty()).isEqualTo("RSA");
        assertThat(jwk.use()).isEqualTo("sig");
        assertThat(jwk.alg()).isEqualTo("RS256");
        assertThat(jwk.kid()).isEqualTo(KID);
        assertThat(jwk.n()).doesNotContain("=");
        assertThat(jwk.e()).doesNotContain("=");
        assertThat(unsigned(jwk.n())).isEqualTo(material.publicKey().getModulus());
        assertThat(unsigned(jwk.e())).isEqualTo(material.publicKey().getPublicExponent());

        UserPrincipal principal = systemPrincipal(42L, "ROLE_SYSTEM");
        MockHttpServletRequest request = new MockHttpServletRequest();
        var tokenResponse = controller.token(
                request,
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
        assertThat(tokenResponse.tokenType()).isEqualTo("Bearer");
        assertThat(tokenResponse.expiresIn()).isEqualTo(300);
        assertThat(tokenResponse.scope())
                .isEqualTo("release:read release:publish");

        Jws<Claims> parsed = Jwts.parser()
                .verifyWith((RSAPublicKey) material.publicKey())
                .build()
                .parseSignedClaims(tokenResponse.accessToken());
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyId()).isEqualTo(KID);
        assertThat(parsed.getHeader().getType()).isEqualTo("JWT");
        assertThat(parsed.getPayload().getIssuer()).isEqualTo(ISSUER);
        assertThat(parsed.getPayload().getAudience())
                .containsExactly(ReleaseControlIdentityService.AUDIENCE);
        assertThat(parsed.getPayload().getSubject()).isEqualTo("erp-user:42");
        assertThat(parsed.getPayload().get("scope", String.class))
                .isEqualTo(ReleaseControlIdentityService.SCOPE);
        assertThat(parsed.getPayload().getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(parsed.getPayload().getNotBefore().toInstant()).isEqualTo(NOW);
        assertThat(parsed.getPayload().getExpiration().toInstant().getEpochSecond()
                - parsed.getPayload().getIssuedAt().toInstant().getEpochSecond())
                .isEqualTo(300);
        assertThat(parsed.getPayload().keySet()).containsExactlyInAnyOrder(
                "iss", "aud", "sub", "scope", "iat", "nbf", "exp", "jti"
        );
    }

    @Test
    void successiveTokensUseDistinctCanonicalUuidV4() {
        ReleaseControlIdentityService service = new ReleaseControlIdentityService(
                load(ISSUER, keyPath, KID),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        Claims first = parse(service.issue(1));
        Claims second = parse(service.issue(1));
        String firstId = first.getId();
        String secondId = second.getId();
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(firstId).matches(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        );
        assertThat(secondId).matches(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        );
    }

    @Test
    void bodyQueryOrNonSystemPrincipalNeverIssuesToken() {
        ReleaseControlIdentityService service = new ReleaseControlIdentityService(
                load(ISSUER, keyPath, KID),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ReleaseControlIdentityController controller =
                new ReleaseControlIdentityController(service);

        MockHttpServletRequest query = new MockHttpServletRequest();
        query.setQueryString("scope=deployment:execute");
        assertThatThrownBy(() -> controller.token(
                query,
                authentication(systemPrincipal(1L, "ROLE_SYSTEM"))
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        MockHttpServletRequest body = new MockHttpServletRequest();
        body.setContent("{}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> controller.token(
                body,
                authentication(systemPrincipal(1L, "ROLE_SYSTEM"))
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        assertThatThrownBy(() -> controller.token(
                new MockHttpServletRequest(),
                authentication(systemPrincipal(1L, "ROLE_ADMIN"))
        )).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.token(
                new MockHttpServletRequest(),
                authentication(systemPrincipal(0L, "ROLE_SYSTEM"))
        )).isInstanceOf(AccessDeniedException.class);
    }

    private static ReleaseControlIdentityKeyMaterial load(
            String issuer,
            Path path,
            String kid
    ) {
        return ReleaseControlIdentityConfiguration.load(issuer, path.toString(), kid);
    }

    private static void writePkcs8(Path path, KeyPair pair) throws Exception {
        byte[] encoded = new PKCS8EncodedKeySpec(pair.getPrivate().getEncoded()).getEncoded();
        String payload = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        Files.writeString(
                path,
                "-----BEGIN PRIVATE KEY-----\n"
                        + payload
                        + "\n-----END PRIVATE KEY-----\n",
                StandardCharsets.US_ASCII
        );
    }

    private static BigInteger unsigned(String value) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(value));
    }

    private static UserPrincipal systemPrincipal(long id, String authority) {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(id);
        when(principal.getUsername()).thenReturn("system@example.invalid");
        when(principal.getAuthorities()).thenAnswer(
                ignored -> Set.of(new SimpleGrantedAuthority(authority))
        );
        return principal;
    }

    private static UsernamePasswordAuthenticationToken authentication(
            UserPrincipal principal
    ) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    private static Claims parse(String token) {
        return Jwts.parser()
                .verifyWith((RSAPublicKey) keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
