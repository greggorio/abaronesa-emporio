package com.baronesa.emporio.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPReceiverAddress {
    @JsonProperty("zip_code")
    private String zipCode;

    @JsonProperty("street_name")
    private String streetName;

    @JsonProperty("street_number")
    private String streetNumber;

    private String floor;
    private String apartment;
}