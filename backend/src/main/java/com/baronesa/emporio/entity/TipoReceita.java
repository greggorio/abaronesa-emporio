package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tipo_receita")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TipoReceita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;
}
