package com.baronesa.emporio.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPSearchPaymentRequest {

    @JsonProperty("external_reference")
    private String externalReference;

    private String status;

    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    @JsonProperty("payment_type_id")
    private String paymentTypeId;

    @JsonProperty("begin_date")
    private LocalDateTime beginDate;

    @JsonProperty("end_date")
    private LocalDateTime endDate;

    private Integer limit;

    private Integer offset;

    @JsonProperty("sort")
    private String sort;

    @JsonProperty("criteria")
    private String criteria;
}