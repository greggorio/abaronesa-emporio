package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "produto_sku")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoSKU {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(length = 100)
    private String variacao; // Ex: "355ml", "600ml", "Dose Simples", "Dose Dupla"

    @Column(length = 50)
    private String codigoBarras;

    @Column(precision = 10, scale = 2)
    private BigDecimal precoCusto;

    @Column(precision = 10, scale = 2)
    private BigDecimal precoVenda;

    // Embalagem vinculada a este SKU (define fator em unidade base do produto)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "embalagem_id")
    private Embalagem embalagem;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "estoque_id")
    private Estoque estoque;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean principal = false; // SKU principal/padrão

    // Auditoria
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    private LocalDateTime atualizadoEm;

    // Método para gerar SKU automático se não fornecido
    public void gerarSKU() {
        if (this.sku == null || this.sku.trim().isEmpty()) {
            String base = produto.getCodigoInterno() != null ?
                         produto.getCodigoInterno() :
                         "PRD" + produto.getId();

            if (variacao != null && !variacao.trim().isEmpty()) {
                // Remove espaços e caracteres especiais
                String varClean = variacao.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
                this.sku = base + "-" + varClean;
            } else {
                this.sku = base + "-DEFAULT";
            }
        }
    }
}
