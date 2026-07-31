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
public class MPTransactionData {
    @JsonProperty("qr_code")
    private String qrCode;

    @JsonProperty("qr_code_base64")
    private String qrCodeBase64;

    @JsonProperty("ticket_url")
    private String ticketUrl;
}