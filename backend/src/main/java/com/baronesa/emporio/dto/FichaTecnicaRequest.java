package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaTecnicaRequest {
    private Long produtoId;
    private Integer rendimento;
    private String observacoes;
    @Builder.Default
    private List<FichaTecnicaItemRequest> itens = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FichaTecnicaItemRequest {
        private Long id; // Null para novos itens
        private Long insumoSkuId;
        private BigDecimal quantidade;
        private Integer ordem;
        private String observacao;
    }
}
