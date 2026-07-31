package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.TipoMidia;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "produto_midia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoMidia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMidia tipo;

    @Column(nullable = false)
    private String url;

    @Column(length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Builder.Default
    private Integer ordem = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean principal = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    // Auditoria
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}