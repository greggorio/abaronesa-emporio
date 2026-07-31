package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ficha_tecnica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FichaTecnica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false, unique = true)
    private Produto produto;

    @Column(name = "custo_total", precision = 10, scale = 2)
    private BigDecimal custoTotal;

    @Column(name = "rendimento")
    private Integer rendimento; // Quantas porções a ficha técnica produz (padrão: 1)

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @OneToMany(mappedBy = "fichaTecnica", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("ordem ASC")
    private List<FichaTecnicaItem> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    private LocalDateTime atualizadoEm;

    // Métodos auxiliares
    public void adicionarItem(FichaTecnicaItem item) {
        itens.add(item);
        item.setFichaTecnica(this);
    }

    public void removerItem(FichaTecnicaItem item) {
        itens.remove(item);
        item.setFichaTecnica(null);
    }

    public void calcularCustoTotal() {
        this.custoTotal = itens.stream()
                .map(FichaTecnicaItem::calcularCusto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
