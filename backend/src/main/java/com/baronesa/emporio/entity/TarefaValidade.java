package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.TarefaValidadeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tarefa_validade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarefaValidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TarefaValidadeStatus status = TarefaValidadeStatus.RASCUNHO;

    @Column(columnDefinition = "text")
    private String observacao;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "tarefa", cascade = CascadeType.ALL)
    private List<TarefaValidadeItem> itens;

    @OneToMany(mappedBy = "tarefa", cascade = CascadeType.ALL)
    private List<TarefaValidadeDivergencia> divergencias;
}
