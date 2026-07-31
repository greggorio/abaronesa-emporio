package com.baronesa.emporio.dto.relatorios;

import java.time.LocalDateTime;
import java.util.List;

public record RelatorioMovimentoCaixaDTO(
        DadosEmpresaDTO empresa,
        MovimentoCaixaFiltroDTO filtros,
        ResumoCaixaDTO resumo,
        List<MovimentoCaixaDetalheDTO> movimentos,
        List<ResumoFormaPagamentoDTO> resumoPorFormaPagamento,
        List<ResumoPorTipoDTO> resumoPorTipo,
        LocalDateTime dataGeracao,
        String usuarioGeracao
) {}
