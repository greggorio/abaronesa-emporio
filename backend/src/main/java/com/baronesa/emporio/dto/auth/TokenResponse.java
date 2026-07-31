package com.baronesa.emporio.dto.auth;

public record TokenResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        String refreshToken
) {
    public static TokenResponse of(String accessToken, String refreshToken, Long expiresIn) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, refreshToken);
    }
}
