package com.baronesa.website.entity.delivery;

import com.baronesa.website.enums.delivery.DeliveryStatus;
import com.baronesa.website.enums.delivery.FulfillmentMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "delivery_order")
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.pending;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_mode", nullable = false)
    private FulfillmentMode fulfillmentMode = FulfillmentMode.DELIVERY;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "dropoff_address")
    private String dropoffAddress;

    @Column(name = "dropoff_notes")
    private String dropoffNotes;

    @Column(name = "uber_delivery_id")
    private String uberDeliveryId;

    @Column(name = "uber_quote_id")
    private String uberQuoteId;

    @Column(name = "uber_tracking_url")
    private String uberTrackingUrl;

    @Column(name = "uber_fee_cents")
    private Integer uberFeeCents;

    @Column(name = "uber_currency")
    private String uberCurrency;

    @Column(name = "pickup_ready_at")
    private Instant pickupReadyAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private DeliveryPayment payment;

    @OneToMany(mappedBy = "deliveryOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryOrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
