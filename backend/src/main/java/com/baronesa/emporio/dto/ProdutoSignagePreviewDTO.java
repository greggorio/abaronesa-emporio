package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoSignagePreviewDTO {
    private String templatePreference;
    private ProductSignagePalette palette;
    private Map<String, String> phrases;
    private ProductInfo product;
    private String imageUrl;
    private String mp4Url;
    private String aiImageUrl;
    private String aiImageHash;
    private Integer aiRevision;
    private Boolean isUsingAiImage;
    private Boolean aiImageAvailable;
    private SignageColorMapping colorMapping;
    private Map<String, String> resolvedColors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductInfo {
        private Long id;
        private String nome;
        private String descricao;
        private BigDecimal precoVenda;
        private Boolean promocao;
        private Boolean destaque;
        private String badgeText;
        private String promoText;
    }
}
