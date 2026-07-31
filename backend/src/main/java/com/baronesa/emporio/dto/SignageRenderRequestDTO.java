package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageRenderRequestDTO {
    private String html;
    private Integer width;
    private Integer height;
    private Integer fps;
    private Integer durationMs;
    private String renderHash;
}
