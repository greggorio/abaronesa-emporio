package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conta_receber")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ContaReceber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente; // Usuario com role CLIENTE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_receita_id", nullable = false)
    private TipoReceita tipoReceita;

    @Column(name = "numero_documento")
    private String numeroDocumento;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(nullable = false)
    private Integer numeroParcelas;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    private boolean recorrente;

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @OneToMany(mappedBy = "contaReceber", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroParcela ASC")
    @Builder.Default
    private List<ContaReceberParcela> parcelas = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        dataCadastro = LocalDateTime.now();
    }

    // Métodos auxiliares
    public void adicionarParcela(ContaReceberParcela parcela) {
        parcelas.add(parcela);
        parcela.setContaReceber(this);
    }

    public void removerParcela(ContaReceberParcela parcela) {
        parcelas.remove(parcela);
        parcela.setContaReceber(null);
    }

    public boolean isQuitada() {
        return !parcelas.isEmpty() && parcelas.stream().allMatch(ContaReceberParcela::isRecebida);
    }

    public BigDecimal getValorRecebido() {
        return parcelas.stream()
                .filter(ContaReceberParcela::isRecebida)
                .map(p -> p.getValorRecebido() != null ? p.getValorRecebido() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getValorPendente() {
        return parcelas.stream()
                .filter(p -> !p.isRecebida())
                .map(p -> p.getValorLiquido() != null ? p.getValorLiquido() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}