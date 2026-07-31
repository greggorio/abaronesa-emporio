package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.StatusPedido;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_mesa_id", nullable = false)
    private SessaoMesa sessaoMesa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_convidado_id", nullable = false)
    private SessaoConvidado sessaoConvidado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPedido status;

    @Column(length = 20)
    private String origem; // pwa | staff

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime aceitoEm;
    private LocalDateTime entregueEm;
    private LocalDateTime canceladoEm;

    @Column(length = 255)
    private String motivoCancelamento;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ItemPedido> itens = new ArrayList<>();

    public void adicionarItem(ItemPedido item) {
        itens.add(item);
        item.setPedido(this);
    }
}

