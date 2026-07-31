package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.TarefaValidadeStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TarefaValidadeDTO {
    private Long id;
    private TarefaValidadeStatus status;
    private String observacao;
    private LocalDateTime criadoEm;
    private LocalDateTime finalizadoEm;
    private List<TarefaValidadeItemResumoDTO> itens;
}
