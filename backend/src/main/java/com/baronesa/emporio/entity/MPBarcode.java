package com.baronesa.emporio.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPBarcode {
    private String type;
    private String content;
    private Integer width;
    private Integer height;
}