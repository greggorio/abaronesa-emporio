package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MercadoPagoPixResponse {

    private String id;
    private String status;
    private BigDecimal amount;

    @JsonProperty("qr_code")
    private String qrCode;

    @JsonProperty("qr_code_base64")
    private String qrCodeBase64;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("created_date")
    private LocalDateTime createdDate;

    @JsonProperty("expiration_date")
    private LocalDateTime expirationDate;

    private String description;

    // Construtor padrão
    public MercadoPagoPixResponse() {
    }

    // Construtor com parâmetros essenciais
    public MercadoPagoPixResponse(String id, String status, BigDecimal amount, String qrCode) {
        this.id = id;
        this.status = status;
        this.amount = amount;
        this.qrCode = qrCode;
        this.createdDate = LocalDateTime.now();
    }

    // Getters e Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public String getQrCodeBase64() {
        return qrCodeBase64;
    }

    public void setQrCodeBase64(String qrCodeBase64) {
        this.qrCodeBase64 = qrCodeBase64;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDateTime expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "MercadoPagoPixResponse{" +
                "id='" + id + '\'' +
                ", status='" + status + '\'' +
                ", amount=" + amount +
                ", externalReference='" + externalReference + '\'' +
                ", createdDate=" + createdDate +
                ", expirationDate=" + expirationDate +
                ", description='" + description + '\'' +
                '}';
    }
}