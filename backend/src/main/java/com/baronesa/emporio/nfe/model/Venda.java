package com.baronesa.emporio.nfe.model;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.ModalidadeFrete;
import com.baronesa.emporio.enums.OrigemVenda;
import com.baronesa.emporio.enums.StatusNfe;
import com.baronesa.emporio.enums.StatusVenda;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Representação em memória da venda usada para emissão de NFC-e.
 * Este modelo não é uma entidade JPA; ele consolida os dados vindos do domínio
 * do restaurante no mesmo formato esperado pelo código legado.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Venda {

    private Long id;
    private String codigo;
    private OrigemVenda origem;
    private Usuario cliente;
    private Usuario vendedor;
    @Builder.Default
    private LocalDateTime dataVenda = LocalDateTime.now();

    @Builder.Default
    private List<VendaItem> itens = new ArrayList<>();

    @Builder.Default
    private List<VendaPagamento> pagamentos = new ArrayList<>();

    private VendaEntrega entrega;

    private Integer numeroNfe;
    private Integer serieNfe;
    private String chaveNfe;
    private StatusNfe statusNfe;
    private String xmlNfe;

    private String pedidoEcommerceId;
    private String ipCliente;
    private String gatewayPagamento;
    private String idPagamentoGateway;
    private String statusPagamentoGateway;
    private String metadataPagamento;

    private BigDecimal valorTotal;
    private BigDecimal descontoTotal;
    private BigDecimal acrescimoTotal;
    private BigDecimal valorFrete;
    private ModalidadeFrete modalidadeFrete;

    @Builder.Default
    private StatusVenda status = StatusVenda.CONFIRMADA;

    private LocalDateTime dataCancelamento;
    private String motivoCancelamento;
    private Usuario usuarioCancelamento;

    private String observacoes;
    private String observacoesInternas;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public void adicionarItem(VendaItem item) {
        if (item == null) {
            return;
        }
        itens.add(item);
    }

    public void adicionarPagamento(VendaPagamento pagamento) {
        if (pagamento == null) {
            return;
        }
        pagamentos.add(pagamento);
    }

    public void definirEntrega(VendaEntrega vendaEntrega) {
        this.entrega = vendaEntrega;
    }

    public boolean isVendaOnline() {
        return origem == OrigemVenda.LOJA_ONLINE;
    }

    public BigDecimal getSubtotal() {
        return itens.stream()
                .map(VendaItem::getValorTotalSeguro)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean precisaEmitirNfe() {
        return status == StatusVenda.CONFIRMADA &&
                (statusNfe == null || statusNfe == StatusNfe.NAO_EMITIDA);
    }
}
