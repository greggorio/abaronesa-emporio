package com.baronesa.emporio.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPRefund {
    private Long id;

    @JsonProperty("payment_id")
    private Long paymentId;

    private BigDecimal amount;

    private Map<String, Object> metadata;

    private String source;

    private String reason;

    @JsonProperty("date_created")
    private OffsetDateTime dateCreated;
}