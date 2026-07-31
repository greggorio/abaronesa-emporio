package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoFiscalDTO {
    private String ncm;
    private String cest;
    private String origem;
    private String cfop;
    private String unidadeTributavel;
    private BigDecimal quantidadeTributavel;
    private BigDecimal valorUnitarioTributavel;
    
    // ICMS
    private String cstIcms;
    private String csosn;
    private BigDecimal aliquotaIcms;
    private BigDecimal aliquotaIcmsST;
    private BigDecimal mva;
    private BigDecimal mvast;
    private BigDecimal reducaoBaseIcms;
    private BigDecimal reducaoBaseIcmsST;
    private Integer modalidadeBcIcms;
    private Integer modalidadeBcIcmsST;
    
    // PIS
    private String cstPis;
    private BigDecimal aliquotaPis;
    private BigDecimal aliquotaPisReais;
    
    // COFINS
    private String cstCofins;
    private BigDecimal aliquotaCofins;
    private BigDecimal aliquotaCofinsReais;
    
    // IPI
    private String cstIpi;
    private BigDecimal aliquotaIpi;
    private String codigoEnquadramentoIpi;
    private String tipoCalculoIpi;
    
    // FCP
    private BigDecimal aliquotaFcp;
    private BigDecimal aliquotaFcpST;
    
    // Outros
    private String informacoesAdicionaisFisco;
    private String codigoBeneficioFiscal;
    private Boolean sujeitoST;
    private Boolean possuiBeneficio;
}
