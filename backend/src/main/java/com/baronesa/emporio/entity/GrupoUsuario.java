package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "grupo_usuario")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GrupoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Builder.Default
    private Boolean ativo = true;
}
