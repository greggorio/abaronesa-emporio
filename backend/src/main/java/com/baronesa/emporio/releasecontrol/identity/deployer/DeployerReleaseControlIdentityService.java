package com.baronesa.emporio.releasecontrol.identity.deployer;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "app.release-control.deployer-identity.enabled",
        havingValue = "true"
)
public class DeployerReleaseControlIdentityService {

    public static final String AUDIENCE = "emporio-release-control-deployer";
    public static final String SCOPE = "deployment:read deployment:execute deployment:rollback";
    public static final long TTL_SECONDS = 300;

    private final DeployerReleaseControlIdentityKeyMaterial keyMaterial;
    private final Clock clock;

    // Required: the class declares two constructors, so Spring cannot pick one on
    // its own and falls back to a no-arg default that does not exist. Without the
    // annotation the bean only fails where it is actually created — production,
    // the single environment with deployer-identity.enabled=true.
    @Autowired
    public DeployerReleaseControlIdentityService(DeployerReleaseControlIdentityKeyMaterial keyMaterial) {
        this(keyMaterial, Clock.systemUTC());
    }

    DeployerReleaseControlIdentityService(
            DeployerReleaseControlIdentityKeyMaterial keyMaterial,
            Clock clock
    ) {
        this.keyMaterial = keyMaterial;
        this.clock = clock;
    }

    String issue(long userId) {
        if (userId < 1) {
            throw new IllegalArgumentException("deployer token subject is invalid");
        }
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .header()
                .type("JWT")
                .keyId(keyMaterial.keyId())
                .and()
                .issuer(keyMaterial.issuer())
                .audience()
                .add(AUDIENCE)
                .and()
                .subject("erp-user:" + userId)
                .claim("scope", SCOPE)
                .issuedAt(Date.from(issuedAt))
                .notBefore(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(TTL_SECONDS)))
                .id(UUID.randomUUID().toString())
                .signWith(keyMaterial.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    DeployerReleaseControlIdentityKeyMaterial keyMaterial() {
        return keyMaterial;
    }
}
