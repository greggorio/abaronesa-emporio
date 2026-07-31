package com.baronesa.website.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        log.error("Max upload size exceeded: {}", exc.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Tamanho do arquivo excede o limite permitido. Por favor, envie arquivos menores.");
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception exc, HttpServletRequest request) {
        if (isSseRequest(request) && isClientDisconnect(exc)) {
            log.debug("SSE client disconnected");
            return ResponseEntity.noContent().build();
        }

        log.error("Unexpected error occurred: ", exc);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Ocorreu um erro inesperado: " + exc.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private boolean isSseRequest(HttpServletRequest request) {
        if (request == null) return false;
        String accept = request.getHeader("Accept");
        String contentType = request.getHeader("Content-Type");
        return (accept != null && accept.contains("text/event-stream"))
                || (contentType != null && contentType.contains("text/event-stream"));
    }

    private boolean isClientDisconnect(Exception exc) {
        Throwable current = exc;
        while (current != null) {
            if (current instanceof IOException) {
                String message = current.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase();
                    if (lower.contains("broken pipe") || lower.contains("connection reset") || lower.contains("connection aborted")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
