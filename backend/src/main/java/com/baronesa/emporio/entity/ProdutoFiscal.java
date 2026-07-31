package com.baronesa.emporio.entity; // Ajustado o pacote para 'bares'

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "produto_fiscal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoFiscal {

    @Id
    private Long produtoId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "produto_id")
    private Produto produto;

    // === CLASSIFICAÇÃO FISCAL ===

    @Column(length = 8)
    private String ncm; // Nomenclatura Comum do Mercosul

    @Column(length = 7)
    private String cest; // Código Especificador da Substituição Tributária

    @Column(length = 1)
    private String origem; // 0-9 conforme legislação

    @Column(length = 2)
    private String unidadeTributavel; // UN, KG, etc

    @Column(length = 4)
    private String cfop; // Código Fiscal de Operações e Prestações padrão

    @Column(precision = 10, scale = 4)
    private BigDecimal quantidadeTributavel;

    @Column(precision = 10, scale = 4)
    private BigDecimal valorUnitarioTributavel;

    // === ICMS ===

    @Column(length = 3)
    private String cstIcms; // 000, 010, 020, etc

    @Column(length = 4)
    private String csosn; // Para Simples Nacional: 101, 102, etc

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaIcms;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaIcmsST;

    @Column(precision = 5, scale = 2)
    private BigDecimal mva; // Margem Valor Agregado ST

    @Column(precision = 5, scale = 2)
    private BigDecimal mvast; // MVA ST Ajustada

    @Column(precision = 5, scale = 2)
    private BigDecimal reducaoBaseIcms;

    @Column(precision = 5, scale = 2)
    private BigDecimal reducaoBaseIcmsST;

    @Column(name = "modalidade_bc_icms")
    private Integer modalidadeBcIcms; // 0-Margem Valor Agregado, 1-Pauta, 2-Preço Tabelado, 3-Valor da Operação

    @Column(name = "modalidade_bc_icms_st")
    private Integer modalidadeBcIcmsST;

    // === PIS ===

    @Column(length = 2)
    private String cstPis;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaPis;

    @Column(precision = 5, scale = 4)
    private BigDecimal aliquotaPisReais; // Para cálculo por unidade

    // === COFINS ===

    @Column(length = 2)
    private String cstCofins;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaCofins;

    @Column(precision = 5, scale = 4)
    private BigDecimal aliquotaCofinsReais; // Para cálculo por unidade

    // === IPI ===

    @Column(length = 2)
    private String cstIpi;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaIpi;

    @Column(length = 3)
    private String codigoEnquadramentoIpi;

    @Column(length = 1)
    private String tipoCalculoIpi; // P-Percentual, V-Valor

    // === FCP (Fundo de Combate à Pobreza) ===

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaFcp;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaFcpST;

    // === INFORMAÇÕES ADICIONAIS ===

    @Column(columnDefinition = "TEXT")
    private String informacoesAdicionaisFisco;

    // Código de benefício fiscal (quando aplicável)
    @Column(length = 10)
    private String codigoBeneficioFiscal;

    // Indica se o produto é sujeito a substituição tributária
    @Column(nullable = false)
    @Builder.Default
    private Boolean sujeitoST = false;

    // Indica se o produto tem isenção/redução
    @Column(nullable = false)
    @Builder.Default
    private Boolean possuiBeneficio = false;

    // === MÉTODOS AUXILIARES ===

    /**
     * Verifica se o produto usa tributação do Simples Nacional
     */
    public boolean isRegimeSimples() {
        return csosn != null && !csosn.isEmpty();
    }

    /**
     * Retorna a situação tributária aplicável (CST ou CSOSN)
     */
    public String getSituacaoTributaria() {
        return isRegimeSimples() ? csosn : cstIcms;
    }

    /**
     * Verifica se tem redução de base de cálculo
     */
    public boolean temReducaoBase() {
        return reducaoBaseIcms != null && reducaoBaseIcms.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Verifica se tem substituição tributária
     */
    public boolean temSubstituicaoTributaria() {
        return Boolean.TRUE.equals(sujeitoST) ||
                (aliquotaIcmsST != null && aliquotaIcmsST.compareTo(BigDecimal.ZERO) > 0);
    }
}
