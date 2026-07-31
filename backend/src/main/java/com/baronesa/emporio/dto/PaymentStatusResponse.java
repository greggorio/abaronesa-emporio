package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentStatusResponse {

    private String id;
    private String status;

    @JsonProperty("status_detail")
    private String statusDetail;

    private BigDecimal amount;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("last_updated")
    private LocalDateTime lastUpdated;

    @JsonProperty("payment_method")
    private String paymentMethod;

    // Construtor padrão
    public PaymentStatusResponse() {
    }

    // Construtor com parâmetros essenciais
    public PaymentStatusResponse(String id, String status, BigDecimal amount) {
        this.id = id;
        this.status = status;
        this.amount = amount;
        this.lastUpdated = LocalDateTime.now();
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

    public String getStatusDetail() {
        return statusDetail;
    }

    public void setStatusDetail(String statusDetail) {
        this.statusDetail = statusDetail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    @Override
    public String toString() {
        return "PaymentStatusResponse{" +
                "id='" + id + '\'' +
                ", status='" + status + '\'' +
                ", statusDetail='" + statusDetail + '\'' +
                ", amount=" + amount +
                ", externalReference='" + externalReference + '\'' +
                ", lastUpdated=" + lastUpdated +
                ", paymentMethod='" + paymentMethod + '\'' +
                '}';
    }
}