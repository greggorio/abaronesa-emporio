package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "conta_receber_parcela")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ContaReceberParcela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_receber_id", nullable = false)
    private ContaReceber contaReceber;

    @Column(nullable = false)
    private Integer numeroParcela;

    @Column(name = "valor_bruto", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorBruto = BigDecimal.ZERO;

    @Column(name = "valor_liquido", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorLiquidoArmazenado = BigDecimal.ZERO;

    @Column(name = "valor_acrescimo", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorAcrescimo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "vencimento")
    private LocalDate vencimento;

    private LocalDate dataRecebimento;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorMulta = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorJuros = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorRecebido;

    @Column(length = 50)
    private String formaRecebimento; // PIX, BOLETO, CARTAO, DINHEIRO, TRANSFERENCIA

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_recebimento_id")
    private Usuario usuarioRecebimento;

    @Column(nullable = false)
    @Builder.Default
    private boolean recebida = false;

    @Column(name = "cobranca_enviada")
    @Builder.Default
    private boolean cobrancaEnviada = false;

    @Column(name = "data_envio_cobranca")
    private LocalDate dataEnvioCobranca;

    @PrePersist
    @PreUpdate
    private void preencherCamposLegados() {
        if (valor == null) {
            valor = BigDecimal.ZERO;
        }
        if (valorMulta == null) valorMulta = BigDecimal.ZERO;
        if (valorJuros == null) valorJuros = BigDecimal.ZERO;
        if (valorDesconto == null) valorDesconto = BigDecimal.ZERO;
        if (valorAcrescimo == null) valorAcrescimo = BigDecimal.ZERO;

        if (valorBruto == null) {
            valorBruto = valor;
        }
        if (vencimento == null) {
            vencimento = dataVencimento;
        }

        BigDecimal liquido = valorBruto != null ? valorBruto : BigDecimal.ZERO;
        liquido = liquido.add(valorAcrescimo != null ? valorAcrescimo : BigDecimal.ZERO);
        liquido = liquido.add(valorJuros != null ? valorJuros : BigDecimal.ZERO);
        liquido = liquido.add(valorMulta != null ? valorMulta : BigDecimal.ZERO);
        liquido = liquido.subtract(valorDesconto != null ? valorDesconto : BigDecimal.ZERO);
        valorLiquidoArmazenado = liquido;
    }

    // Métodos auxiliares
    public boolean isVencida() {
        return !recebida && dataVencimento.isBefore(LocalDate.now());
    }

    public long getDiasAtraso() {
        if (!isVencida()) return 0;
        return ChronoUnit.DAYS.between(dataVencimento, LocalDate.now());
    }

    // Calcula valor líquido considerando multa, juros e desconto
    public BigDecimal getValorLiquido() {
        BigDecimal valorLiquido = valor;
        if (valorMulta != null) valorLiquido = valorLiquido.add(valorMulta);
        if (valorJuros != null) valorLiquido = valorLiquido.add(valorJuros);
        if (valorDesconto != null) valorLiquido = valorLiquido.subtract(valorDesconto);
        return valorLiquido;
    }
}
