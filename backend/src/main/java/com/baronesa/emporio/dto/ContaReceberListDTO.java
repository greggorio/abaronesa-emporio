package com.baronesa.emporio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ContaReceberListDTO {

    private Long id;                      // ID da parcela
    private Long idConta;                 // ID da conta a receber
    private Long tipoReceitaId;          // ID do tipo de receita
    private String clienteNome;              // Nome do cliente
    private String descricao;            // Descrição da conta
    private Integer numeroParcela;       // Nº da parcela
    private LocalDate dataVencimento;
    private LocalDate dataRecebimento;
    private BigDecimal valor;
    private BigDecimal valorLiquido;
    private BigDecimal valorRecebido;
    private String formaRecebimento;
    private boolean recebida;
    private long diasAtraso;
}
