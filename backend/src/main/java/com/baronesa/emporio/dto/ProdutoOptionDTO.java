package com.baronesa.emporio.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoOptionDTO {
    private Long id;
    private String label;
}