package com.baronesa.website.dto;

import com.baronesa.website.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
    private Long id;
    private String sessionCode;
    private SessionStatus status;
    private LocalDateTime createdAt;
    private Integer currentQuestionIndex;
    private Integer totalQuestions;
    private Integer questionTimeLimit;
    private Boolean autoAdvance;
    private String qrCodeUrl; // URL do QR Code gerado
}
