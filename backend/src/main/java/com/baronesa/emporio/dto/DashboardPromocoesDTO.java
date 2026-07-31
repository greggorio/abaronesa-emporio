package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardPromocoesDTO {
    
    private Integer produtosEmPromocao;
    private Integer descontoMedio;
    private BigDecimal impactoVendas;
    private Integer vendasPromocao;
    private Integer vendasNormais;
    private List<DashboardPromocaoProdutoDTO> produtosEmPromocaoLista;
    
    // Construtores
    public DashboardPromocoesDTO() {}
    
    public DashboardPromocoesDTO(Integer produtosEmPromocao, Integer descontoMedio, BigDecimal impactoVendas, 
                                Integer vendasPromocao, Integer vendasNormais, 
                                List<DashboardPromocaoProdutoDTO> produtosEmPromocaoLista) {
        this.produtosEmPromocao = produtosEmPromocao;
        this.descontoMedio = descontoMedio;
        this.impactoVendas = impactoVendas;
        this.vendasPromocao = vendasPromocao;
        this.vendasNormais = vendasNormais;
        this.produtosEmPromocaoLista = produtosEmPromocaoLista;
    }
    
    // Getters e Setters
    public Integer getProdutosEmPromocao() {
        return produtosEmPromocao;
    }
    
    public void setProdutosEmPromocao(Integer produtosEmPromocao) {
        this.produtosEmPromocao = produtosEmPromocao;
    }
    
    public Integer getDescontoMedio() {
        return descontoMedio;
    }
    
    public void setDescontoMedio(Integer descontoMedio) {
        this.descontoMedio = descontoMedio;
    }
    
    public BigDecimal getImpactoVendas() {
        return impactoVendas;
    }
    
    public void setImpactoVendas(BigDecimal impactoVendas) {
        this.impactoVendas = impactoVendas;
    }
    
    public Integer getVendasPromocao() {
        return vendasPromocao;
    }
    
    public void setVendasPromocao(Integer vendasPromocao) {
        this.vendasPromocao = vendasPromocao;
    }
    
    public Integer getVendasNormais() {
        return vendasNormais;
    }
    
    public void setVendasNormais(Integer vendasNormais) {
        this.vendasNormais = vendasNormais;
    }
    
    public List<DashboardPromocaoProdutoDTO> getProdutosEmPromocaoLista() {
        return produtosEmPromocaoLista;
    }
    
    public void setProdutosEmPromocaoLista(List<DashboardPromocaoProdutoDTO> produtosEmPromocaoLista) {
        this.produtosEmPromocaoLista = produtosEmPromocaoLista;
    }
}