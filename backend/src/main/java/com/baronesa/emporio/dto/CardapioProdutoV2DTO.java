package com.baronesa.emporio.dto;

import com.baronesa.emporio.dto.ProdutoDisponibilidadeDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardapioProdutoV2DTO {
    private Long id;
    private String nome;
    private String descricao;
    private BigDecimal preco; // para SIMPLES/UNIFICADA
    private BigDecimal preco_promocional;
    private String origem_desconto;
    private String imagemPrincipal;
    private Boolean destaque;
    private Integer ordem;
    private List<CardapioSkuDTO> skus;
    private List<ProdutoMidiaDTO> midias;
    private Boolean produto_disponivel;
    private Boolean produto_em_promocao;
    private List<ProdutoDisponibilidadeDTO> horarios_disponiveis;
    private Boolean temHarmonizacao;
}
