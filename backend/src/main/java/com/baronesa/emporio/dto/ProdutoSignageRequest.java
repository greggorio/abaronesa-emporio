package com.baronesa.emporio.dto;

import com.baronesa.emporio.dto.ProductSignagePalette;
import com.baronesa.emporio.dto.deserializer.StringOrMapDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoSignageRequest {
    private Boolean enabled;
    private String templatePreference;
    @JsonDeserialize(using = StringOrMapDeserializer.class)
    private Map<String, String> phrases;
    private ProductSignagePalette palette;
    private String metadataSource;
    private Boolean useAiImage;
    private SignageColorMapping colorMapping;
}
