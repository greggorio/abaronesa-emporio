package com.baronesa.emporio.releasecontrol.identity.deployer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.release-control.deployer-identity.enabled",
        havingValue = "true"
)
public class DeployerReleaseControlIdentityConfiguration {

    static final int MAX_PRIVATE_KEY_BYTES = 16 * 1024;
    static final int MIN_RSA_BITS = 3072;
    static final Pattern KEY_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{15,63}$");
    private static final String PEM_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_END = "-----END PRIVATE KEY-----";

    @Bean
    DeployerReleaseControlIdentityKeyMaterial deployerReleaseControlIdentityKeyMaterial(
            @Value("${app.release-control.deployer-identity.issuer}") String issuer,
            @Value("${app.release-control.deployer-identity.private-key-path}") String privateKeyPath,
            @Value("${app.release-control.deployer-identity.key-id}") String keyId
    ) {
        return load(issuer, privateKeyPath, keyId);
    }

    static DeployerReleaseControlIdentityKeyMaterial load(
            String issuer,
            String privateKeyPath,
            String keyId
    ) {
        validateIssuer(issuer);
        if (keyId == null || !KEY_ID_PATTERN.matcher(keyId).matches()) {
            throw new IllegalStateException("deployer release control identity key id is invalid");
        }
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            throw new IllegalStateException("deployer release control identity private key path is required");
        }
        Path path;
        try {
            path = Path.of(privateKeyPath);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("deployer release control identity private key path is invalid", ex);
        }
        if (!path.isAbsolute()
                || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("deployer release control identity private key file is invalid");
        }
        try {
            long size = Files.size(path);
            if (size < 1 || size > MAX_PRIVATE_KEY_BYTES) {
                throw new IllegalStateException("deployer release control identity private key size is invalid");
            }
            byte[] encoded = decodePkcs8(Files.readAllBytes(path));
            PrivateKey parsed = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
            if (!(parsed instanceof RSAPrivateCrtKey privateKey)
                    || privateKey.getModulus().bitLength() < MIN_RSA_BITS
                    || privateKey.getPublicExponent().signum() <= 0) {
                throw new IllegalStateException("deployer release control identity requires RSA CRT 3072");
            }
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(
                            privateKey.getModulus(),
                            privateKey.getPublicExponent()
                    ));
            return new DeployerReleaseControlIdentityKeyMaterial(
                    issuer,
                    keyId,
                    privateKey,
                    publicKey
            );
        } catch (IOException | GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("deployer release control identity private key is invalid", ex);
        }
    }

    private static byte[] decodePkcs8(byte[] fileBytes) {
        String pem = new String(fileBytes, StandardCharsets.US_ASCII);
        String normalized = pem.replace("\r\n", "\n");
        if (!normalized.startsWith(PEM_BEGIN + "\n")
                || !(normalized.endsWith("\n" + PEM_END)
                || normalized.endsWith("\n" + PEM_END + "\n"))
                || normalized.contains("ENCRYPTED")
                || normalized.contains("RSA PRIVATE KEY")) {
            throw new IllegalStateException("deployer release control identity requires unencrypted PKCS8 PEM");
        }
        int start = PEM_BEGIN.length() + 1;
        int end = normalized.indexOf("\n" + PEM_END, start);
        String payload = normalized.substring(start, end);
        if (payload.isBlank() || !payload.matches("[A-Za-z0-9+/=\\n]+")) {
            throw new IllegalStateException("deployer release control identity PKCS8 payload is invalid");
        }
        return Base64.getMimeDecoder().decode(payload);
    }

    private static void validateIssuer(String issuer) {
        if (issuer == null || issuer.isBlank() || issuer.endsWith("/")) {
            throw new IllegalStateException("deployer release control identity issuer is invalid");
        }
        URI uri;
        try {
            uri = URI.create(issuer);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("deployer release control identity issuer is invalid", ex);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!uri.isAbsolute()
                || host == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || ("http".equalsIgnoreCase(scheme)
                && !isLoopbackHost(host))) {
            throw new IllegalStateException("deployer release control identity issuer is invalid");
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }

    static String base64UrlUnsigned(BigInteger value) {
        byte[] raw = value.toByteArray();
        int offset = raw.length > 1 && raw[0] == 0 ? 1 : 0;
        byte[] unsigned = new byte[raw.length - offset];
        System.arraycopy(raw, offset, unsigned, 0, unsigned.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned);
    }
}
