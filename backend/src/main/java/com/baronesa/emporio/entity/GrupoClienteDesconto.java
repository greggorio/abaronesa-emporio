package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "grupo_cliente_desconto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoClienteDesconto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_cliente_id", nullable = false)
    private GrupoCliente grupoCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategoria_id")
    private Subcategoria subcategoria;

    @Column(name = "desconto_percentual", precision = 5, scale = 2, nullable = false)
    private BigDecimal descontoPercentual; // Ex: 10.00 para 10%

    @Column(name = "ativo")
    @Builder.Default
    private Boolean ativo = true;
}
