package com.baronesa.emporio.releasecontrol.identity;

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
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.release-control.identity.enabled",
        havingValue = "true"
)
public class ReleaseControlIdentityConfiguration {

    static final int MAX_PRIVATE_KEY_BYTES = 16 * 1024;
    static final int MIN_RSA_BITS = 3072;
    static final Pattern KEY_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{15,63}$");
    private static final String PEM_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_END = "-----END PRIVATE KEY-----";

    @Bean
    ReleaseControlIdentityKeyMaterial releaseControlIdentityKeyMaterial(
            @Value("${app.release-control.identity.issuer}") String issuer,
            @Value("${app.release-control.identity.private-key-path}") String privateKeyPath,
            @Value("${app.release-control.identity.key-id}") String keyId,
            @Value("${app.release-control.identity.generate-key-if-absent:false}") boolean generateIfAbsent
    ) {
        if (generateIfAbsent) {
            generateKeyIfAbsent(privateKeyPath);
        }
        return load(issuer, privateKeyPath, keyId);
    }

    /**
     * Cria a chave do emissor quando ela ainda nao existe.
     *
     * <p>Habilitado somente no perfil de desenvolvimento. Sem isso a ponte teria de
     * ficar desligada por padrao — ligada sem chave, a aplicacao nao sobe — e cada
     * desenvolvedor precisaria lembrar de gerar o arquivo e exportar variaveis
     * antes de iniciar. A chave nasce fora do repositorio, com permissao restrita,
     * e nunca substitui um arquivo existente. Em producao a propriedade e falsa e
     * a chave continua sendo material provisionado pelo operador.
     */
    static void generateKeyIfAbsent(String privateKeyPath) {
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            return;
        }
        Path path = Path.of(privateKeyPath);
        if (Files.exists(path)) {
            return;
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_RSA_BITS);
            byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
            String pem = PEM_BEGIN + "\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                            .encodeToString(encoded)
                    + "\n" + PEM_END + "\n";
            Files.writeString(path, pem, StandardCharsets.US_ASCII);
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Sistema de arquivos sem POSIX: o conteudo ja foi escrito.
            }
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "release control identity key could not be generated", exception);
        }
    }

    static ReleaseControlIdentityKeyMaterial load(
            String issuer,
            String privateKeyPath,
            String keyId
    ) {
        validateIssuer(issuer);
        if (keyId == null || !KEY_ID_PATTERN.matcher(keyId).matches()) {
            throw new IllegalStateException("release control identity key id is invalid");
        }
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            throw new IllegalStateException("release control identity private key path is required");
        }
        Path path;
        try {
            path = Path.of(privateKeyPath);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("release control identity private key path is invalid", ex);
        }
        if (!path.isAbsolute()
                || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("release control identity private key file is invalid");
        }
        try {
            long size = Files.size(path);
            if (size < 1 || size > MAX_PRIVATE_KEY_BYTES) {
                throw new IllegalStateException("release control identity private key size is invalid");
            }
            byte[] encoded = decodePkcs8(Files.readAllBytes(path));
            PrivateKey parsed = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
            if (!(parsed instanceof RSAPrivateCrtKey privateKey)
                    || privateKey.getModulus().bitLength() < MIN_RSA_BITS
                    || privateKey.getPublicExponent().signum() <= 0) {
                throw new IllegalStateException("release control identity requires RSA CRT 3072");
            }
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(
                            privateKey.getModulus(),
                            privateKey.getPublicExponent()
                    ));
            return new ReleaseControlIdentityKeyMaterial(
                    issuer,
                    keyId,
                    privateKey,
                    publicKey
            );
        } catch (IOException | GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("release control identity private key is invalid", ex);
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
            throw new IllegalStateException("release control identity requires unencrypted PKCS8 PEM");
        }
        int start = PEM_BEGIN.length() + 1;
        int end = normalized.indexOf("\n" + PEM_END, start);
        String payload = normalized.substring(start, end);
        if (payload.isBlank() || !payload.matches("[A-Za-z0-9+/=\\n]+")) {
            throw new IllegalStateException("release control identity PKCS8 payload is invalid");
        }
        return Base64.getMimeDecoder().decode(payload);
    }

    private static void validateIssuer(String issuer) {
        if (issuer == null || issuer.isBlank() || issuer.endsWith("/")) {
            throw new IllegalStateException("release control identity issuer is invalid");
        }
        URI uri;
        try {
            uri = URI.create(issuer);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("release control identity issuer is invalid", ex);
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
            throw new IllegalStateException("release control identity issuer is invalid");
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
