package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recebimento_mercadoria")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"fornecedor", "usuario", "itens"})
@EqualsAndHashCode(exclude = {"fornecedor", "usuario", "itens"})
public class RecebimentoMercadoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_nf", nullable = false, length = 20)
    private String numeroNf;

    @Column(name = "chave_nfe", length = 44)
    private String chaveNfe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @Column(name = "data_recebimento", nullable = false)
    private LocalDateTime dataRecebimento;

    @Column(name = "data_emissao_nf")
    private LocalDate dataEmissaoNf;

    @Column(name = "valor_total", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "quantidade_itens")
    @Builder.Default
    private Integer quantidadeItens = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusRecebimento status = StatusRecebimento.PENDENTE;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "xml_nfe", columnDefinition = "TEXT")
    private String xmlNfe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @OneToMany(mappedBy = "recebimento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecebimentoItem> itens = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (dataRecebimento == null) {
            dataRecebimento = LocalDateTime.now();
        }
        if (status == null) {
            status = StatusRecebimento.PENDENTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Métodos auxiliares
    public void addItem(RecebimentoItem item) {
        itens.add(item);
        item.setRecebimento(this);
        recalcularTotais();
    }

    public void removeItem(RecebimentoItem item) {
        itens.remove(item);
        item.setRecebimento(null);
        recalcularTotais();
    }

    public void recalcularTotais() {
        this.valorTotal = itens.stream()
                .map(RecebimentoItem::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.quantidadeItens = itens.size();
    }

    public boolean podeEditar() {
        return status == StatusRecebimento.PENDENTE;
    }

    public boolean podeFinalizar() {
        return status == StatusRecebimento.PENDENTE &&
                itens != null &&
                !itens.isEmpty() &&
                itens.stream().allMatch(item ->
                        item.getQuantidade() != null &&
                                item.getQuantidade().compareTo(BigDecimal.ZERO) > 0
                );
    }

    public boolean podeCancelar() {
        return status == StatusRecebimento.PENDENTE || status == StatusRecebimento.FINALIZADO;
    }

    public void finalizar() {
        if (!podeFinalizar()) {
            throw new IllegalStateException("Recebimento não pode ser finalizado");
        }
        this.status = StatusRecebimento.FINALIZADO;
    }

    public void cancelar() {
        if (!podeCancelar()) {
            throw new IllegalStateException("Recebimento não pode ser cancelado");
        }
        this.status = StatusRecebimento.CANCELADO;
    }
}