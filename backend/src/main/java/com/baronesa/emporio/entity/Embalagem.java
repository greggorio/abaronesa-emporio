package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "embalagem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Embalagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, length = 60)
    private String nome; // Ex.: Garrafa, Dose, Lata

    @Column(name = "fator_base", nullable = false)
    private Integer fatorBase; // quantidade em unidade base do produto (ex.: 990 ml)

    @Column(length = 50)
    private String codigoBarras;

    @Builder.Default
    @Column(nullable = false)
    private Boolean permiteVenda = true; // pode ser usado diretamente na venda

    @Builder.Default
    @Column(nullable = false)
    private Boolean principal = false; // embalagem principal do produto (ex.: Garrafa)

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    private LocalDateTime atualizadoEm;
}

