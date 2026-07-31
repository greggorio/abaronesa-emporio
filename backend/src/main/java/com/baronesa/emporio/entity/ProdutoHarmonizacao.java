package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "produto_harmonizacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoHarmonizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_principal_id", nullable = false)
    private Produto produtoPrincipal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_harmonizado_id", nullable = false)
    private Produto produtoHarmonizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_harmonizado_id")
    private ProdutoSKU skuHarmonizado;

    @Column(length = 50)
    private String tipo; // Ex: COMPLEMENTAR, CONTRASTE, SEMELHANCA

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Builder.Default
    private Integer ordem = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
