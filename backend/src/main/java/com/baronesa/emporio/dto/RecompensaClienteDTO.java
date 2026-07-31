package com.baronesa.emporio.dto;

import com.baronesa.emporio.entity.TipoRecompensa;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecompensaClienteDTO(
        Long id,
        String nome,
        String descricao,
        TipoRecompensa tipo,
        Integer pontosNecessarios,
        String imagemUrl,
        Long produtoId,
        BigDecimal descontoPercentual,
        BigDecimal descontoValor,
        BigDecimal descontoValorMaximo,
        LocalDate validadeInicio,
        LocalDate validadeFim,
        Boolean disponivel,
        Boolean podeResgatar,
        Integer faltamPontos
) {
}