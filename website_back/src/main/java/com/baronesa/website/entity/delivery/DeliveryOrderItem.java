package com.baronesa.website.entity.delivery;

import com.baronesa.website.enums.delivery.DeliveryItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "delivery_order_item")
public class DeliveryOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_order_id")
    private DeliveryOrder deliveryOrder;

    @Column(name = "produto_id")
    private Long produtoId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "nome")
    private String nome;

    @Column(name = "quantidade")
    private Integer quantidade;

    @Column(name = "observacoes")
    private String observacoes;

    @Column(name = "size")
    private String size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryItemStatus status = DeliveryItemStatus.queued;

    @Column(name = "kds_visible", nullable = false)
    private Boolean kdsVisible = false;

    @Column(name = "estacao")
    private String estacao;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
