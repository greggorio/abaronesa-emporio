package com.baronesa.emporio.releasecontrol.identity.deployer;

import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;

public record DeployerReleaseControlIdentityKeyMaterial(
        String issuer,
        String keyId,
        RSAPrivateCrtKey privateKey,
        RSAPublicKey publicKey
) {
}
