package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.StatusSessao;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessao_mesa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessaoMesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSessao status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime abertaEm;

    private LocalDateTime fechadaEm;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "self_checkout_liberado")
    private Boolean selfCheckoutLiberado;

    @Column(name = "self_checkout_liberado_em")
    private LocalDateTime selfCheckoutLiberadoEm;
}
