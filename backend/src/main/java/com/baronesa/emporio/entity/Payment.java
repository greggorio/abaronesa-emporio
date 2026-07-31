package com.baronesa.emporio.entity;

import com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.model.PaymentMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentGatewayType gateway;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private PaymentMethod method;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "normalized_status", length = 50)
    private NormalizedPaymentStatus normalizedStatus;

    @Column(name = "provider_status", length = 50)
    private String providerStatus;

    @Column(name = "provider_status_detail", length = 100)
    private String providerStatusDetail;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
