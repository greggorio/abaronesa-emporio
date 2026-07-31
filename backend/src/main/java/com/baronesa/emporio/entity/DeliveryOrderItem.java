package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.DeliveryOrderItemStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_order_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_order_id", nullable = false)
    @JsonIgnore
    private DeliveryOrder deliveryOrder;

    @Column(name = "produto_id")
    private Long produtoId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "variacao")
    private String variacao;

    @Column(name = "quantidade", nullable = false)
    private Integer quantidade;

    @Column(name = "preco_unit_cents", nullable = false)
    private Integer precoUnitCents;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "estacao", length = 50)
    private String estacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DeliveryOrderItemStatus status = DeliveryOrderItemStatus.QUEUED;

    @Column(name = "kds_archived", nullable = false)
    @Builder.Default
    private Boolean kdsArchived = false;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
