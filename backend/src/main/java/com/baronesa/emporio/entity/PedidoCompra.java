package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.StatusPedidoCompra;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido_compra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor; // opcional no MVP

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario; // quem criou/alterou

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private StatusPedidoCompra status = StatusPedidoCompra.RASCUNHO;

    private LocalDate dataPrevista;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PedidoCompraItem> itens = new ArrayList<>();

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        criadoEm = now;
        atualizadoEm = now;
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public void addItem(PedidoCompraItem item) {
        itens.add(item);
        item.setPedido(this);
    }

    public void removeItem(PedidoCompraItem item) {
        itens.remove(item);
        item.setPedido(null);
    }
}

