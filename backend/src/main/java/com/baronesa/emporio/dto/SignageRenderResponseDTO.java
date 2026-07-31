package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageRenderResponseDTO {
    private String url;
    private String renderHash;
    private Boolean cached;
    private Integer durationMs;
    private Integer width;
    private Integer height;
}
