package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categoria_despesa")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CategoriaDespesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;
}
