package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conta_pagar")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ContaPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_despesa_id", nullable = false)
    private CategoriaDespesa categoriaDespesa;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(nullable = false)
    private Integer numeroParcelas;

    private boolean recorrente;

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @OneToMany(mappedBy = "contaPagar", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroParcela ASC")
    @Builder.Default
    private List<ContaPagarParcela> parcelas = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        dataCadastro = LocalDateTime.now();
    }

    // Métodos auxiliares
    public void adicionarParcela(ContaPagarParcela parcela) {
        parcelas.add(parcela);
        parcela.setContaPagar(this);
    }

    public void removerParcela(ContaPagarParcela parcela) {
        parcelas.remove(parcela);
        parcela.setContaPagar(null);
    }

    public boolean isQuitada() {
        return parcelas.stream().allMatch(ContaPagarParcela::isPaga);
    }

    public BigDecimal getValorPago() {
        return parcelas.stream()
                .filter(ContaPagarParcela::isPaga)
                .map(ContaPagarParcela::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getValorPendente() {
        return parcelas.stream()
                .filter(p -> !p.isPaga())
                .map(ContaPagarParcela::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
