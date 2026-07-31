package com.baronesa.emporio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ValidadeAlertaLoteDTO {
    private Long estoqueLoteId;
    private Long skuId;
    private String skuCodigo;
    private String skuDescricao;
    private Long produtoId;
    private String produtoNome;
    private String lote;
    private LocalDate dataValidade;
    private BigDecimal quantidade;
    private Integer diasParaVencer;
    private Integer vidaUtilDias;
    private Integer alertaPercentual;
    private Double percentualVidaRestante;
    private String status;
}