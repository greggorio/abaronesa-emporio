package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "conta_pagar_parcela")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ContaPagarParcela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_pagar_id", nullable = false)
    private ContaPagar contaPagar;

    @Column(nullable = false)
    private Integer numeroParcela;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate dataVencimento;

    private LocalDate dataPagamento;

    @Column(length = 50)
    private String formaPagamento; // PIX, BOLETO, CARTAO, DINHEIRO, TRANSFERENCIA

    @Column(nullable = false)
    @Builder.Default
    private boolean paga = false;

    // Método auxiliar
    public boolean isVencida() {
        return !paga && dataVencimento.isBefore(LocalDate.now());
    }
}
