package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.StatusPedidoCompraItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pedido_compra_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoCompraItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_compra_id", nullable = false)
    private PedidoCompra pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id")
    private ProdutoSKU sku; // vendáveis

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "embalagem_id")
    private Embalagem embalagem; // insumos

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade; // unidade de compra (UN/pacote)

    @Column(name = "quantidade_base")
    private Integer quantidadeBase; // derivado para insumos

    @Column(name = "custo_unitario", precision = 15, scale = 4)
    private BigDecimal custoUnitario;

    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "recebido_base")
    @Builder.Default
    private Integer recebidoBase = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private StatusPedidoCompraItem status = StatusPedidoCompraItem.PENDENTE;
}

