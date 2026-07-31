package com.baronesa.emporio.nfe.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dados de entrega mantidos para compatibilidade, mesmo que não
 * sejam usados pelo restaurante.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendaEntrega {

    private Long id;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String estado;
    private String cep;
    private String referencia;

    private String nomeRecebedor;
    private String telefoneContato;
    private String emailNotificacao;

    private String transportadora;
    private String servicoEnvio;
    private String codigoRastreio;
    private String urlRastreamento;
    private Integer prazoEntregaDias;

    private LocalDateTime dataPreparacao;
    private LocalDateTime dataEnvio;
    private LocalDateTime dataEntregaPrevista;
    private LocalDateTime dataEntregaRealizada;

    private BigDecimal valorFrete;
    private BigDecimal valorSeguro;
    private BigDecimal valorTotalEnvio;

    private Integer pesoTotalGramas;
    private Integer volumeTotalCm3;
}
