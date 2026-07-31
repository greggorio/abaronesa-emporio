package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    private String icone; // opcional: nome do ícone ou emoji

    @OneToMany(mappedBy = "categoria", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Subcategoria> subcategorias;

    private String cover; // URL ou caminho do arquivo

    @Builder.Default
    private Boolean exibirNoCardapio = false; // Se a categoria deve aparecer no cardápio

    @Builder.Default
    private Integer ordem = 0; // Ordem de exibição no cardápio
}