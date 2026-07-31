package com.baronesa.emporio.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.baronesa.emporio.entity.MPPayment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPPaymentResponse {

    private MPPayment payment;

    private List<MPError> errors;

    private String message;

    private String status;

    @JsonProperty("status_code")
    private Integer statusCode;
}