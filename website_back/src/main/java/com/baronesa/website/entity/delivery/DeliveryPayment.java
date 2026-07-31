package com.baronesa.website.entity.delivery;

import com.baronesa.website.enums.delivery.DeliveryPaymentStatus;
import com.baronesa.website.enums.delivery.FulfillmentMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "delivery_payment")
public class DeliveryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryPaymentStatus status = DeliveryPaymentStatus.pending;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_mode", nullable = false)
    private FulfillmentMode fulfillmentMode = FulfillmentMode.DELIVERY;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "fee_cents")
    private Integer feeCents;

    @Column(name = "currency")
    private String currency;

    @Column(name = "quote_id")
    private String quoteId;

    @Column(name = "qr_payload")
    private String qrPayload;

    @Column(name = "provider_reference")
    private String providerReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
