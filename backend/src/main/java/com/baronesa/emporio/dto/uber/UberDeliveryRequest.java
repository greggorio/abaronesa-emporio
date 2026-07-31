package com.baronesa.emporio.dto.uber;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UberDeliveryRequest {

    @JsonProperty("pickup_address")
    private String pickupAddress;
    @JsonProperty("pickup_name")
    private String pickupName;
    @JsonProperty("pickup_phone_number")
    private String pickupPhoneNumber;
    @JsonProperty("pickup_notes")
    private String pickupNotes;

    @JsonProperty("dropoff_address")
    private String dropoffAddress;
    @JsonProperty("dropoff_name")
    private String dropoffName;
    @JsonProperty("dropoff_phone_number")
    private String dropoffPhoneNumber;
    @JsonProperty("dropoff_notes")
    private String dropoffNotes;

    @JsonProperty("manifest_items")
    private List<ManifestItem> manifestItems;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("deliverable_action")
    private String deliverableAction;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ManifestItem {
        private String name;
        private Integer quantity;
        private String size;
        @JsonProperty("must_be_upright")
        private Boolean mustBeUpright;
    }
}
