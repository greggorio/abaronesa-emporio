package com.baronesa.emporio.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPItem {
    private String id;
    private String title;
    private String description;

    @JsonProperty("category_id")
    private String categoryId;

    private Integer quantity;

    @JsonProperty("unit_price")
    private BigDecimal unitPrice;
}
