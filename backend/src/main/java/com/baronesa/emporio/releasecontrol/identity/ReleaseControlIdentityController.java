package com.baronesa.emporio.releasecontrol.identity;

import com.baronesa.emporio.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/release-control/identity")
@ConditionalOnProperty(
        name = "app.release-control.identity.enabled",
        havingValue = "true"
)
public class ReleaseControlIdentityController {

    private final ReleaseControlIdentityService service;

    public ReleaseControlIdentityController(ReleaseControlIdentityService service) {
        this.service = service;
    }

    @GetMapping("/jwks")
    public ResponseEntity<JwksResponse> jwks() {
        ReleaseControlIdentityKeyMaterial material = service.keyMaterial();
        JwkKey key = new JwkKey(
                "RSA",
                "sig",
                "RS256",
                material.keyId(),
                ReleaseControlIdentityConfiguration.base64UrlUnsigned(
                        material.publicKey().getModulus()
                ),
                ReleaseControlIdentityConfiguration.base64UrlUnsigned(
                        material.publicKey().getPublicExponent()
                )
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new JwksResponse(List.of(key)));
    }

    @PostMapping("/token")
    @PreAuthorize("hasRole('SYSTEM')")
    public TokenResponse token(
            HttpServletRequest request,
            Authentication authentication
    ) throws IOException {
        if (request.getQueryString() != null
                || request.getInputStream().readNBytes(1).length != 0) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)
                || principal.getId() == null
                || principal.getId() < 1
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_SYSTEM".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("publisher identity requires SYSTEM");
        }
        return new TokenResponse(
                service.issue(principal.getId()),
                "Bearer",
                ReleaseControlIdentityService.TTL_SECONDS,
                ReleaseControlIdentityService.SCOPE
        );
    }

    public record JwksResponse(List<JwkKey> keys) {
    }

    public record JwkKey(
            String kty,
            String use,
            String alg,
            String kid,
            String n,
            String e
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String scope
    ) {
    }
}
