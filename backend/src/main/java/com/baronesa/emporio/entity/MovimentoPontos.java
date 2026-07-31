package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.OrigemMovimentoPontos;
import com.baronesa.emporio.enums.TipoMovimentoPontos;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimentos_pontos")
public class MovimentoPontos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoMovimentoPontos tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 10)
    private OrigemMovimentoPontos origem;

    @Column(name = "referencia_tipo", length = 30)
    private String referenciaTipo;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "pontos", nullable = false)
    private Integer pontos;

    @Column(name = "saldo_apos", nullable = false)
    private Integer saldoApos;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @PrePersist
    protected void onPrePersist() {
        this.dataHora = LocalDateTime.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        // dataHora is not updated on update as per common practice for transaction logs
        // If it needs to be updated, uncomment the line below:
        // this.dataHora = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getCliente() {
        return cliente;
    }

    public void setCliente(Usuario cliente) {
        this.cliente = cliente;
    }

    public TipoMovimentoPontos getTipo() {
        return tipo;
    }

    public void setTipo(TipoMovimentoPontos tipo) {
        this.tipo = tipo;
    }

    public OrigemMovimentoPontos getOrigem() {
        return origem;
    }

    public void setOrigem(OrigemMovimentoPontos origem) {
        this.origem = origem;
    }

    public String getReferenciaTipo() {
        return referenciaTipo;
    }

    public void setReferenciaTipo(String referenciaTipo) {
        this.referenciaTipo = referenciaTipo;
    }

    public Long getReferenciaId() {
        return referenciaId;
    }

    public void setReferenciaId(Long referenciaId) {
        this.referenciaId = referenciaId;
    }

    public Integer getPontos() {
        return pontos;
    }

    public void setPontos(Integer pontos) {
        this.pontos = pontos;
    }

    public Integer getSaldoApos() {
        return saldoApos;
    }

    public void setSaldoApos(Integer saldoApos) {
        this.saldoApos = saldoApos;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
