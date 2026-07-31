package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"id_grupo", "permissao"})
})
public class PermissoesGrupos {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_grupo")
    private Long idGrupo;

    private String permissao;
}
