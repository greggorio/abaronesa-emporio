package com.baronesa.emporio.dto.relatorios;

import java.time.LocalDateTime;
import java.util.List;

public record RelatorioVendasAnaliticoDTO(
        DadosEmpresaDTO empresa,
        RelatorioVendasFiltroDTO filtros,
        ResumoVendasProdutoDTO resumo,
        List<VendaItemDetalheDTO> itens,
        LocalDateTime dataGeracao,
        String usuarioGeracao
) {
}
