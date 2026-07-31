package com.baronesa.emporio.dto.lookup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO específico para lookup de produtos
 * Consolida informações do Produto e seus SKUs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoLookupDTO {

    private Long id;
    private String codigoInterno;
    private String nome;
    private String descricao;
    private Integer estoqueTotal;
    private Double preco;
    private Boolean temVariacoes;
    private Integer qtdSkus;
    private String codigosBarras; // Concatenação dos códigos de barras dos SKUs
    private Boolean insumo;

    /**
     * Converte para o formato esperado pelo frontend
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        // Campos padrão do lookup
        map.put("id", id);
        map.put("value", id);

        // Corrigir label para não exibir "null -"
        String label;
        if (codigoInterno != null && !codigoInterno.trim().isEmpty()) {
            label = codigoInterno + " - " + nome;
        } else {
            label = nome; // Se não tem código interno, usar apenas o nome
        }
        map.put("label", label);

        // Campos específicos do produto
        map.put("codigo", codigoInterno);
        map.put("descricao", nome); // O frontend espera "descricao", não "nome"
        map.put("estoqueAtual", estoqueTotal != null ? estoqueTotal : 0);
        map.put("preco", preco);

        // Informações sobre variações
        if (qtdSkus != null && qtdSkus > 0) {
            map.put("temVariacoes", qtdSkus > 1);
            map.put("qtdSkus", qtdSkus);
        }

        // Códigos de barras para busca
        if (codigosBarras != null) {
            map.put("codigosBarras", codigosBarras);
        }

        // Flag para o frontend decidir entre seleção por produto (insumo) ou por SKU (não-insumo)
        map.put("insumo", Boolean.TRUE.equals(insumo));

        return map;
    }
}
