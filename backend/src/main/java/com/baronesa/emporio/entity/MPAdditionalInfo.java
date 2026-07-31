package com.baronesa.emporio.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPAdditionalInfo {

    @JsonProperty("ip_address")
    private String ipAddress;

    private List<MPItem> items;

    private MPShipments shipments;

    private MPBarcode barcode;
}