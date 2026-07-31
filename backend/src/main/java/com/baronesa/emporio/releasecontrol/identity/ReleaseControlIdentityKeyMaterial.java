package com.baronesa.emporio.releasecontrol.identity;

import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;

public record ReleaseControlIdentityKeyMaterial(
        String issuer,
        String keyId,
        RSAPrivateCrtKey privateKey,
        RSAPublicKey publicKey
) {
}
