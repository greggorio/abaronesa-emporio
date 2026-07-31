package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.LocalPreparacao;
import com.baronesa.emporio.enums.TipoPrecificacao;
import com.baronesa.emporio.enums.TipoProduto;
import com.baronesa.emporio.enums.UnidadeMedida;
import com.baronesa.emporio.enums.UnidadeBase;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoRequest {
    private Long id;

    private String nome;
    private String setor;
    private String descricao;
    private String codigoInterno;
    private String codigoBarras;
    private TipoProduto tipo;
    private UnidadeMedida unidadeMedida;
    private UnidadeBase unidadeBase;

    private Long categoriaId;
    private Long subcategoriaId;
    private Long fornecedorId;

    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    private TipoPrecificacao tipoPrecificacao;

    private Boolean controlaEstoque;
    private Boolean controlaValidade;
    private Integer vidaUtilDias;
    private BigDecimal estoqueAtual;

    private Boolean ativo;
    private Boolean vendavel;
    private Boolean insumo;
    private Boolean exibirNoCardapio;
    private Boolean promocao;
    private Boolean destaque;
    private Boolean necessitaPreparacao;
    private Boolean producaoPropria;
    private LocalPreparacao localPreparacao;
    private Integer ordem;

    private String imagemPrincipal;
    private List<ProdutoSKUDTO> skus;
    // Para INSUMO: mínimo na unidade base (estoque_produto.estoque_minimo_base)
    private Integer estoqueMinimoBase;
    private List<ProdutoMidiaDTO> midias;

    // Para vendável SIMPLES (1 SKU): mínimo por SKU único
    private Integer estoqueMinimoSkuUnico;
    
    // === CAMPOS FISCAIS ESSENCIAIS (Flat) ===
    private String ncm;
    private String cest;
    private String origem;
    private String csosn;
    private String cfop;
    
    // Objeto completo para detalhes avançados se necessário
    private ProdutoFiscalDTO produtoFiscal;
    private ProdutoSignageRequest signage;
    private Boolean signageEnabled;
}
