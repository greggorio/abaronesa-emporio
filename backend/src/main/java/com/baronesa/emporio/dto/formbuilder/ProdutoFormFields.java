package com.baronesa.emporio.dto.formbuilder;

import com.baronesa.emporio.enums.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO dedicado ao FormBuilder para exposição de campos editáveis
 * do cadastro de produtos, incluindo mínimos por SKU único e por base (insumo).
 * Não é entidade JPA; serve apenas para reflexão do builder.
 */
@Data
public class ProdutoFormFields {
    private Long id;

    // Dados básicos
    private String nome;
    private String descricao;
    private String codigoInterno;
    private String codigoBarras;
    private String setor;
    private TipoProduto tipo;
    private UnidadeMedida unidadeMedida;
    private UnidadeBase unidadeBase;

    // Relacionamentos (por id)
    private Long categoriaId;
    private Long subcategoriaId;
    private Long fornecedorId;

    // Preços
    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    private TipoPrecificacao tipoPrecificacao;

    // Estoque e flags
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
    private Boolean signageEnabled;
    private Boolean necessitaPreparacao;
    private Boolean producaoPropria;
    private LocalPreparacao localPreparacao;
    private Integer ordem;
    private String imagemPrincipal;

    // Campos adicionais – mínimos
    // Vendável SIMPLES: mínimo por SKU único (salvo em estoque do SKU)
    private Integer estoqueMinimoSkuUnico;
    // Insumo: mínimo em unidade base (salvo em estoque_produto)
    private Integer estoqueMinimoBase;

    // === CAMPOS FISCAIS ESSENCIAIS ===
    private String ncm;
    private String cest;
    private String origem;
    private String csosn;
    private String cfop;

    // Coleções – apenas para o builder ter ciência (não usados diretamente aqui)
    private List<Object> skus;
    private List<Object> midias;
}
