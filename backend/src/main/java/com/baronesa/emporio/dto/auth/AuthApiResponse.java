package com.baronesa.emporio.dto.auth;

public record AuthApiResponse(
        boolean success,
        String message
) {
    public static AuthApiResponse success(String message) {
        return new AuthApiResponse(true, message);
    }

    public static AuthApiResponse error(String message) {
        return new AuthApiResponse(false, message);
    }
}
