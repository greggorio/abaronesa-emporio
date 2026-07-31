package com.baronesa.emporio.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MPCard {
    private String id;

    @JsonProperty("first_six_digits")
    private String firstSixDigits;

    @JsonProperty("last_four_digits")
    private String lastFourDigits;

    @JsonProperty("expiration_month")
    private Integer expirationMonth;

    @JsonProperty("expiration_year")
    private Integer expirationYear;

    @JsonProperty("date_created")
    private OffsetDateTime dateCreated;

    @JsonProperty("date_last_updated")
    private OffsetDateTime dateLastUpdated;

    private MPCardholder cardholder;
}
