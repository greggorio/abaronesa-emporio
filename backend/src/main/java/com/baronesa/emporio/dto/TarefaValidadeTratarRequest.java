package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.TarefaValidadeDivergenciaAcao;
import lombok.Data;

@Data
public class TarefaValidadeTratarRequest {
    private TarefaValidadeDivergenciaAcao acao;
}
