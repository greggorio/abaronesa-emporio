package com.baronesa.emporio.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPPayer {

    private String id;

    private String email;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("entity_type")
    private String entityType;

    private String type;

    private MPIdentification identification;

    private MPPhone phone;

    @JsonProperty("date_created")
    private LocalDate dateCreated;

    @JsonProperty("last_purchase")
    private LocalDate lastPurchase;
}