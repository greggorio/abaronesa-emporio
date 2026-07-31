package com.baronesa.emporio.controller.advice;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.exception.SessionClosedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import br.com.swconsultoria.nfe.exception.NfeException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NfeException.class)
    public ResponseEntity<Map<String, Object>> handleNfeException(NfeException ex) {
        log.error("Erro na emissão da NFC-e: {}", ex.getMessage(), ex);

        Map<String, Object> error = new HashMap<>();
        error.put("code", "internal_error");
        error.put("message", extractMeaningfulMessage(ex));

        Map<String, Object> response = new HashMap<>();
        response.put("error", error);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Erro de validação");

        Map<String, Object> error = new HashMap<>();
        error.put("code", "validation_error");
        error.put("message", message);

        Map<String, Object> response = new HashMap<>();
        response.put("error", error);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", "bad_request");
        error.put("message", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", error);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", "invalid_credentials");
        error.put("message", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", error);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "Erro na requisição";
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }

        Map<String, Object> error = new HashMap<>();
        error.put("code", status == HttpStatus.CONFLICT ? "conflict" : "http_error");
        error.put("message", reason);

        Map<String, Object> response = new HashMap<>();
        response.put("error", error);

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex, HttpServletResponse response) {
        if (isEventStreamResponse(response)) {
            log.warn("Erro em SSE: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }

        if (ex instanceof IOException) {
            log.debug("I/O exception ao responder o cliente: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }

        log.error("Erro não tratado: {}", ex.getMessage(), ex);

        Map<String, Object> error = new HashMap<>();
        error.put("code", "internal_error");
        error.put("message", "Ocorreu um erro");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("error", error);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseBody);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("code", "not_found");
        error.put("message", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", error);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", "business_error");
        error.put("message", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("error", error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(SessionClosedException.class)
    public ResponseEntity<Map<String, Object>> handleSessionClosed(SessionClosedException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", "session_closed");
        error.put("message", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", error);

        return ResponseEntity.status(HttpStatus.GONE).body(response);
    }

    private String extractMeaningfulMessage(Throwable ex) {
        String message = findFirstNonBlankMessage(ex);
        if (message == null) {
            return "Ocorreu um erro";
        }

        String[] lines = message.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("- ")) {
                line = line.substring(2).trim();
            }
            if (!line.isEmpty()) {
                return line;
            }
        }

        return message.trim();
    }

    private String findFirstNonBlankMessage(Throwable ex) {
        if (ex == null) {
            return null;
        }
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage().trim();
        }
        return findFirstNonBlankMessage(ex.getCause());
    }

    private boolean isEventStreamResponse(HttpServletResponse response) {
        if (response == null) {
            return false;
        }
        String contentType = response.getContentType();
        return contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
