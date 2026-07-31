package com.baronesa.emporio.dto;

import java.math.BigDecimal;

public class DashboardPromocaoProdutoDTO {
    
    private String nome;
    private BigDecimal total;
    private BigDecimal precoOriginal;
    private BigDecimal precoComDesconto;
    private Integer desconto;
    private Integer vendas;
    private Integer progressWidth;
    
    // Construtores
    public DashboardPromocaoProdutoDTO() {}
    
    public DashboardPromocaoProdutoDTO(String nome, BigDecimal total, BigDecimal precoOriginal, 
                                      BigDecimal precoComDesconto, Integer desconto, 
                                      Integer vendas, Integer progressWidth) {
        this.nome = nome;
        this.total = total;
        this.precoOriginal = precoOriginal;
        this.precoComDesconto = precoComDesconto;
        this.desconto = desconto;
        this.vendas = vendas;
        this.progressWidth = progressWidth;
    }
    
    // Getters e Setters
    public String getNome() {
        return nome;
    }
    
    public void setNome(String nome) {
        this.nome = nome;
    }
    
    public BigDecimal getTotal() {
        return total;
    }
    
    public void setTotal(BigDecimal total) {
        this.total = total;
    }
    
    public BigDecimal getPrecoOriginal() {
        return precoOriginal;
    }
    
    public void setPrecoOriginal(BigDecimal precoOriginal) {
        this.precoOriginal = precoOriginal;
    }
    
    public BigDecimal getPrecoComDesconto() {
        return precoComDesconto;
    }
    
    public void setPrecoComDesconto(BigDecimal precoComDesconto) {
        this.precoComDesconto = precoComDesconto;
    }
    
    public Integer getDesconto() {
        return desconto;
    }
    
    public void setDesconto(Integer desconto) {
        this.desconto = desconto;
    }
    
    public Integer getVendas() {
        return vendas;
    }
    
    public void setVendas(Integer vendas) {
        this.vendas = vendas;
    }
    
    public Integer getProgressWidth() {
        return progressWidth;
    }
    
    public void setProgressWidth(Integer progressWidth) {
        this.progressWidth = progressWidth;
    }
}