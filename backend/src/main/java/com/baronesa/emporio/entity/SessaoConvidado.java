package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessao_convidado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessaoConvidado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_mesa_id", nullable = false)
    private SessaoMesa sessaoMesa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(length = 100)
    private String nomeExibicao;

    @Column(unique = true, nullable = false, length = 255)
    private String guestToken;

    @Column(length = 255)
    private String deviceFingerprint;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime entrouEm;

    private LocalDateTime saiuEm;

    @Builder.Default
    @Column
    private Boolean host = false;

    @PrePersist
    public void prePersist() {
        if (host == null) host = false;
    }
}
