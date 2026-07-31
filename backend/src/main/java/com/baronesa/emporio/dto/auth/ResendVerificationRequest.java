package com.baronesa.emporio.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para solicitar reenvio de email de verificação
 */
public record ResendVerificationRequest(
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email
) {}
