package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.TarefaValidadeItemAcao;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TarefaValidadeItemResumoDTO {
    private Long id;
    private Long skuId;
    private String produtoNome;
    private String lote;
    private LocalDate dataValidade;
    private BigDecimal quantidade;
    private TarefaValidadeItemAcao acao;
}
