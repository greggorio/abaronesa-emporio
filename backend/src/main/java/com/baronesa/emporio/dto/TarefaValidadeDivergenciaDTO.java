package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.TarefaValidadeDivergenciaAcao;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TarefaValidadeDivergenciaDTO {
    private Long id;
    private Long tarefaId;
    private Long skuId;
    private String skuCodigo;
    private String produtoNome;
    private BigDecimal estoqueAgregado;
    private BigDecimal somaLotes;
    private BigDecimal diferenca;
    private TarefaValidadeDivergenciaAcao acaoTomada;
    private Long movimentoEstoqueId;
    private LocalDateTime criadoEm;
}
