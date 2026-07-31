package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "estoque_produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstoqueProduto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false, unique = true)
    private Produto produto;

    @Builder.Default
    @Column(name = "quantidade_base", nullable = false)
    private Integer quantidadeBase = 0;

    @Builder.Default
    @Column(name = "reservado_base", nullable = false)
    private Integer reservadoBase = 0;

    @Builder.Default
    @Column(name = "estoque_minimo_base", nullable = false)
    private Integer estoqueMinimoBase = 0;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    private LocalDateTime atualizadoEm;
}

