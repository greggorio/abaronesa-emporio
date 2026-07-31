package com.baronesa.emporio.dto.auth;

import java.time.LocalDateTime;

/**
 * DTO para resposta de verificação de email
 */
public record EmailVerificationResponse(
        boolean success,
        String message,
        String email,
        LocalDateTime verifiedAt
) {
    /**
     * Cria resposta de sucesso
     */
    public static EmailVerificationResponse success(String message, String email, LocalDateTime verifiedAt) {
        return new EmailVerificationResponse(true, message, email, verifiedAt);
    }

    /**
     * Cria resposta de erro
     */
    public static EmailVerificationResponse error(String message) {
        return new EmailVerificationResponse(false, message, null, null);
    }
}
