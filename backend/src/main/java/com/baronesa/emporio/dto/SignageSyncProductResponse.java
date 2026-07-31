package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para resposta da sincronização de produto com signage-api
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageSyncProductResponse {
    
    private Boolean success;
    private String action; // "created" ou "updated"
    private ScreenDTO screen;
    private PlaylistItemDTO playlistItem;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenDTO {
        private String id;
        private String title;
        private String type;
        private String mediaUrl;
        private String renderHash;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaylistItemDTO {
        private String id;
        private String playlistId;
        private Integer order;
        private Integer durationSeconds;
    }
}
