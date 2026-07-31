package com.baronesa.emporio.dto.relatorios;

import java.time.LocalDateTime;
import java.util.List;

public record RelatorioVendasDTO(
        DadosEmpresaDTO empresa,
        RelatorioVendasFiltroDTO filtros,
        ResumoVendasDTO resumo,
        List<PagamentoDetalheDTO> pagamentos,
        List<ResumoFormaPagamentoDTO> resumoPorFormaPagamento,
        LocalDateTime dataGeracao,
        String usuarioGeracao
) {
}
