package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "recompensas")
public class Recompensa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "pontos_necessarios", nullable = false)
    private Integer pontosNecessarios;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoRecompensa tipo;

    @Column(name = "desconto_percentual", precision = 5, scale = 2)
    private BigDecimal descontoPercentual;

    @Column(name = "desconto_valor", precision = 12, scale = 2)
    private BigDecimal descontoValor;

    @Column(name = "desconto_valor_maximo", precision = 12, scale = 2)
    private BigDecimal descontoValorMaximo;

    @Column(name = "produto_id")
    private Long produtoId;

    @Column(name = "estoque", columnDefinition = "INT NULL")
    private Integer estoque;

    @Column(name = "ativo", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean ativo;

    @Column(name = "validade_inicio")
    private LocalDate validadeInicio;

    @Column(name = "validade_fim")
    private LocalDate validadeFim;

    @Column(name = "imagem_url", length = 500)
    private String imageUrl;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onPrePersist() {
        criadoEm = LocalDateTime.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Integer getPontosNecessarios() {
        return pontosNecessarios;
    }

    public void setPontosNecessarios(Integer pontosNecessarios) {
        this.pontosNecessarios = pontosNecessarios;
    }

    public TipoRecompensa getTipo() {
        return tipo;
    }

    public void setTipo(TipoRecompensa tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getDescontoPercentual() {
        return descontoPercentual;
    }

    public void setDescontoPercentual(BigDecimal descontoPercentual) {
        this.descontoPercentual = descontoPercentual;
    }

    public BigDecimal getDescontoValor() {
        return descontoValor;
    }

    public void setDescontoValor(BigDecimal descontoValor) {
        this.descontoValor = descontoValor;
    }

    public BigDecimal getDescontoValorMaximo() {
        return descontoValorMaximo;
    }

    public void setDescontoValorMaximo(BigDecimal descontoValorMaximo) {
        this.descontoValorMaximo = descontoValorMaximo;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(Long produtoId) {
        this.produtoId = produtoId;
    }

    public Integer getEstoque() {
        return estoque;
    }

    public void setEstoque(Integer estoque) {
        this.estoque = estoque;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDate getValidadeInicio() {
        return validadeInicio;
    }

    public void setValidadeInicio(LocalDate validadeInicio) {
        this.validadeInicio = validadeInicio;
    }

    public LocalDate getValidadeFim() {
        return validadeFim;
    }

    public void setValidadeFim(LocalDate validadeFim) {
        this.validadeFim = validadeFim;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
