package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificacao", indexes = {
    @Index(name = "idx_notificacao_convidado_lida", columnList = "sessao_convidado_id,lida"),
    @Index(name = "idx_notificacao_criado_em", columnList = "criado_em")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_mesa_id", nullable = false)
    private SessaoMesa sessaoMesa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_convidado_id", nullable = false)
    private SessaoConvidado sessaoConvidado;

    @Column(nullable = false, length = 50)
    private String tipo; // guest_joined, order_created

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String mensagem;

    @Builder.Default
    @Column(nullable = false)
    private Boolean lida = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime lidaEm;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @PrePersist
    public void prePersist() {
        if (lida == null) lida = false;
    }
}
