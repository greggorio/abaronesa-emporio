package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.DeliveryOrderStatus;
import com.baronesa.emporio.enums.TipoDeliveryPedido;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "delivery_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDeliveryPedido tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DeliveryOrderStatus status = DeliveryOrderStatus.PENDING_PAYMENT;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_cpf")
    private String customerCpf;

    @Column(name = "dropoff_address", columnDefinition = "TEXT")
    private String dropoffAddress;

    @Column(name = "dropoff_notes", columnDefinition = "TEXT")
    private String dropoffNotes;

    @Column(name = "delivery_fee_cents", nullable = false)
    @Builder.Default
    private Integer deliveryFeeCents = 0;

    @Column(name = "items_total_cents", nullable = false)
    @Builder.Default
    private Integer itemsTotalCents = 0;

    @Column(name = "total_cents", nullable = false)
    @Builder.Default
    private Integer totalCents = 0;

    @Column(name = "currency", length = 10)
    @Builder.Default
    private String currency = "BRL";

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_gateway", length = 50)
    private PaymentGatewayType providerGateway;

    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    @Column(name = "mp_payment_id", length = 100)
    private String mpPaymentId;

    @Column(name = "mp_status", length = 50)
    private String mpStatus;

    @Column(name = "mp_status_detail", length = 100)
    private String mpStatusDetail;

    @Column(name = "mp_payment_method", length = 50)
    private String mpPaymentMethod;

    @Column(name = "mp_qr_code", columnDefinition = "TEXT")
    private String mpQrCode;

    @Column(name = "mp_qr_code_base64", columnDefinition = "TEXT")
    private String mpQrCodeBase64;

    @Column(name = "mp_raw_response", columnDefinition = "jsonb")
    private String mpRawResponse;

    @Column(name = "uber_delivery_id", length = 100)
    private String uberDeliveryId;

    @Column(name = "uber_tracking_url", columnDefinition = "TEXT")
    private String uberTrackingUrl;

    @Column(name = "uber_status", length = 50)
    private String uberStatus;

    @Column(name = "uber_dropoff_eta")
    private LocalDateTime uberDropoffEta;

    @Column(name = "uber_pickup_eta")
    private LocalDateTime uberPickupEta;

    @Column(name = "uber_pickup_address", columnDefinition = "TEXT")
    private String uberPickupAddress;

    @Column(name = "cliente_id")
    private Long clienteId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "kds_published_at")
    private LocalDateTime kdsPublishedAt;

    @Column(name = "canceled_reason", length = 255)
    private String canceledReason;

    @OneToMany(mappedBy = "deliveryOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryOrderItem> itens = new ArrayList<>();

    public void addItem(DeliveryOrderItem item) {
        if (itens == null) {
            itens = new ArrayList<>();
        }
        itens.add(item);
        item.setDeliveryOrder(this);
    }
}
