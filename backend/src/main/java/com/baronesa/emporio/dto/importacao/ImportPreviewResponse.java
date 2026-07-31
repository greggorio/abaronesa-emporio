package com.baronesa.emporio.dto.importacao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewResponse {
    private Integer total;
    private Integer validos;
    private Integer duplicadosInternos;
    private Integer invalidos;
    private List<ProdutoPreviewItem> linhasValidas;  // até 50 primeiras linhas válidas ordenadas por nome
    private ExemploInvalido exemploInvalido;  // um exemplo de linha inválida
    private List<CategoriaDetectada> categoriasDetectadas;  // categorias detectadas no arquivo
}