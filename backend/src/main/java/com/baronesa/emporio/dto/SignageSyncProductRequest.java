package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para requisição de sincronização de produto com signage-api
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageSyncProductRequest {
    
    private String title;
    private String mediaUrl;
    private String renderHash;
    private Integer durationSeconds;
}
