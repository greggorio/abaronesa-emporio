package com.baronesa.emporio.dto.relatorios;

import java.time.LocalDateTime;
import java.util.List;

public record RelatorioVendasProdutoDTO(
        DadosEmpresaDTO empresa,
        RelatorioVendasFiltroDTO filtros,
        ResumoVendasProdutoDTO resumo,
        List<ProdutoVendaDTO> produtos,
        LocalDateTime dataGeracao,
        String usuarioGeracao
) {
}
