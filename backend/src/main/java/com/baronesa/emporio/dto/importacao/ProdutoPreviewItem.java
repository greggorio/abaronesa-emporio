package com.baronesa.emporio.dto.importacao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoPreviewItem {
    private String nome;
    private String descricao;
    private String codigoInterno;
    private String precoCusto;  // Em formato string com 2 casas decimais
    private String precoVenda;  // Em formato string com 2 casas decimais
    private Integer margemLucro;  // default simples (mantém compatibilidade)
    private String tipoPrecificacao; // SIMPLES
    private String unidadeMedida;
    private String unidadeBase;  // fallback para "UNIDADE"
    private String tipoCalculoMargem; // SOBRE_CUSTO
    private Boolean ativo;
    private String ncm;
    private String grupo; // nome do grupo original (categoria sugerida)
    private Long categoriaId; // se não encontrada: null; preencher se encontrada
}