package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.StatusChamado;
import com.baronesa.emporio.enums.TipoChamado;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chamado", indexes = {
    @Index(name = "idx_chamado_status", columnList = "status"),
    @Index(name = "idx_chamado_criado_em", columnList = "criado_em")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chamado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_mesa_id", nullable = false)
    private SessaoMesa sessaoMesa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_convidado_id")
    private SessaoConvidado sessaoConvidado; // Quem chamou (opcional)

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TipoChamado tipo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private StatusChamado status;

    @Column(length = 500)
    private String observacao;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(length = 100)
    private String atendidoPor; // Nome do staff que atendeu

    private LocalDateTime atendidoEm;

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = StatusChamado.PENDENTE;
        }
    }
}
