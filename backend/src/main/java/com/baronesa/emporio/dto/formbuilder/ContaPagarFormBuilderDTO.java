package com.baronesa.emporio.dto.formbuilder;

import lombok.*;

import java.math.BigDecimal;

/**
 * DTO específico para o form-builder da entidade ContaPagar.
 * Este DTO agrega informações de múltiplas entidades relacionadas
 * e campos derivados para apresentação no frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContaPagarFormBuilderDTO {

    private Long id;
    private String fornecedor;
    private String categoria;
    private String descricao;
    private BigDecimal valorTotal;
    private String parcelas;
    private String status;
    private String recorrente;
}
