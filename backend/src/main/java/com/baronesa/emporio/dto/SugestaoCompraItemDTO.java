package com.baronesa.emporio.dto;

import java.math.BigDecimal;

public record SugestaoCompraItemDTO(
        Long produtoId,
        String produtoNome,
        Boolean insumo,
        Long skuId,
        String sku,
        Long embalagemId,
        String embalagemNome,
        Integer fatorBase,
        Integer estoqueAtualBase,
        Integer estoqueMinimoBase,
        Integer estoqueAtualSku,
        Integer estoqueMinimoSku,
        BigDecimal quantidadeSugerida,
        BigDecimal custoUnitario,
        String motivo
) {}

