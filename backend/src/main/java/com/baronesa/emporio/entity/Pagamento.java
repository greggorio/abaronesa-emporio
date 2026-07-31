package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.StatusPagamento;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_mesa_id", nullable = false)
    private SessaoMesa sessaoMesa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_convidado_id")
    private SessaoConvidado sessaoConvidado; // Beneficiário: null=mesa toda, preenchido=pessoa específica

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagante_id")
    private SessaoConvidado pagante; // Quem está pagando (pode ser diferente do beneficiário)

    @Column(length = 20, nullable = false)
    private String metodo; // pix | card | cash (MVP: pix)

    @Column(name = "cartao_tipo", length = 20)
    private String cartaoTipo; // credito | debito

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private StatusPagamento status;

    @Column(name = "valor", precision = 10, scale = 2, nullable = false)
    private BigDecimal valor;

    @Column(name = "valor_base", precision = 10, scale = 2)
    private BigDecimal valorBase; // Consumo/valor principal

    @Column(name = "valor_taxa_servico", precision = 10, scale = 2)
    private BigDecimal valorTaxaServico; // Parcela da taxa de serviço (gorjeta)

    @Column(name = "valor_couvert", precision = 10, scale = 2)
    private BigDecimal valorCouvert; // Parcela do couvert artístico

    @Column(name = "percentual_taxa_servico", precision = 5, scale = 2)
    private BigDecimal percentualTaxaServico; // Percentual aplicado no momento do pagamento

    @Column(name = "inclui_taxa_servico")
    private Boolean incluiTaxaServico; // Flag indicando se este pagamento incluiu taxa de serviço

    @Column(name = "qr_payload", columnDefinition = "TEXT")
    private String qrPayload;

    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    @Column(name = "self_checkout_origem", length = 30)
    private String selfCheckoutOrigem;

    @Column(name = "self_checkout_resolvido")
    private Boolean selfCheckoutResolvido;

    @Column(name = "self_checkout_resolvido_em")
    private LocalDateTime selfCheckoutResolvidoEm;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime pagoEm;
}
