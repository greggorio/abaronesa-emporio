package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baronesa.emporio.enums.TarefaValidadeItemAcao;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TarefaValidadeItemRequest {
    private Long skuId;
    private String lote;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataValidade;
    private BigDecimal quantidade;
    private TarefaValidadeItemAcao acao = TarefaValidadeItemAcao.SET;
}
