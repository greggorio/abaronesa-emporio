package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.MovimentoCaixaDTO;
import com.baronesa.emporio.dto.MovimentoCaixaRequest;
import com.baronesa.emporio.entity.MovimentoCaixa;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.enums.TipoMovimentoCaixa;
import com.baronesa.emporio.enums.TipoOperacao;
import com.baronesa.emporio.repository.MovimentoCaixaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MovimentoCaixaService {

    private final MovimentoCaixaRepository repository;

    // 🔹 Registro manual (via frontend)
    public MovimentoCaixaDTO registrarMovimento(MovimentoCaixaRequest request, Usuario responsavel) {
        MovimentoCaixa movimento = MovimentoCaixa.builder()
                .dataHora(LocalDateTime.now())
                .tipo(request.tipo())
                .valor(request.valor())
                .meioPagamento(request.meioPagamento())
                .afetaCaixa(request.afetaCaixa())
                .operacao(request.operacao())
                .observacao(request.observacao())
                .referenciaId(request.referenciaId())
                .referenciaTipo(request.referenciaTipo())
                .responsavel(responsavel)
                .build();

        repository.save(movimento);
        return toDTO(movimento);
    }

    // 🔹 Pagamento de mesa
    public void registrarPagamentoMesa(BigDecimal valor, TipoFormaPagamento formaPagamento,
                                       Long pagamentoId, Usuario responsavel, String observacao) {
        registrar(TipoMovimentoCaixa.PAGAMENTO_MESA, valor, formaPagamento, true,
                "PAGAMENTO", pagamentoId, responsavel, TipoOperacao.ENTRADA, observacao);
    }

    // 🔹 Gorjeta
    public void registrarGorjeta(BigDecimal valor, TipoFormaPagamento formaPagamento,
                                 Usuario responsavel, String observacao) {
        registrar(TipoMovimentoCaixa.GORJETA, valor, formaPagamento, true,
                null, null, responsavel, TipoOperacao.ENTRADA, observacao);
    }

    // 🔹 Contas a pagar / receber
    public void registrarMovimentoConta(TipoMovimentoCaixa tipo, BigDecimal valor,
                                        TipoFormaPagamento formaPagamento,
                                        Long contaId, String referenciaTipo,
                                        Usuario responsavel) {
        // Contas a pagar = SAIDA, Contas a receber = ENTRADA
        TipoOperacao operacao = tipo == TipoMovimentoCaixa.CONTAS_PAGAR ?
                TipoOperacao.SAIDA : TipoOperacao.ENTRADA;

        registrar(tipo, valor, formaPagamento, true, referenciaTipo, contaId, responsavel,
                operacao, null);
    }

    // 🔹 Estorno
    public void registrarEstorno(BigDecimal valor, TipoFormaPagamento formaPagamento,
                                 Long referenciaId, Usuario responsavel, String observacao) {
        // Estorno sempre é SAIDA (devolução de dinheiro)
        registrar(TipoMovimentoCaixa.ESTORNO, valor, formaPagamento, true,
                "PAGAMENTO", referenciaId, responsavel, TipoOperacao.SAIDA, observacao);
    }

    // 🔹 Caixa inicial
    public void registrarCaixaInicial(BigDecimal valor, Usuario responsavel) {
        registrar(TipoMovimentoCaixa.CAIXA_INICIAL, valor, TipoFormaPagamento.DINHEIRO, true,
                null, null, responsavel, TipoOperacao.ENTRADA, "Abertura de caixa");
    }

    // 🔹 Sangria
    public void registrarSangria(BigDecimal valor, Usuario responsavel, String observacao) {
        registrar(TipoMovimentoCaixa.SANGRIA, valor, TipoFormaPagamento.DINHEIRO, true,
                null, null, responsavel, TipoOperacao.SAIDA, observacao);
    }

    // 🔹 Reforço
    public void registrarReforco(BigDecimal valor, Usuario responsavel, String observacao) {
        registrar(TipoMovimentoCaixa.REFORCO, valor, TipoFormaPagamento.DINHEIRO, true,
                null, null, responsavel, TipoOperacao.ENTRADA, observacao);
    }

    // 🔹 Método genérico reutilizável
    public void registrar(TipoMovimentoCaixa tipo, BigDecimal valor,
                          TipoFormaPagamento meioPagamento, boolean afetaCaixa,
                          String referenciaTipo, Long referenciaId,
                          Usuario responsavel, TipoOperacao operacao, String observacao) {

        MovimentoCaixa movimento = MovimentoCaixa.builder()
                .dataHora(LocalDateTime.now())
                .tipo(tipo)
                .valor(valor)
                .meioPagamento(meioPagamento)
                .afetaCaixa(afetaCaixa)
                .referenciaTipo(referenciaTipo)
                .referenciaId(referenciaId)
                .responsavel(responsavel)
                .operacao(operacao)
                .observacao(observacao)
                .build();

        repository.save(movimento);
    }

    // 🔹 Conversor para DTO
    public MovimentoCaixaDTO toDTO(MovimentoCaixa m) {
        return MovimentoCaixaDTO.builder()
                .id(m.getId())
                .dataHora(m.getDataHora())
                .tipo(m.getTipo())
                .valor(m.getValor())
                .meioPagamento(m.getMeioPagamento())
                .afetaCaixa(m.isAfetaCaixa())
                .operacao(m.getOperacao())
                .observacao(m.getObservacao())
                .referenciaId(m.getReferenciaId())
                .referenciaTipo(m.getReferenciaTipo())
                .responsavelId(m.getResponsavel() != null ? m.getResponsavel().getId() : null)
                .responsavelNome(m.getResponsavel() != null ? m.getResponsavel().getNome() : null)
                .build();
    }

    /**
     * Determina automaticamente o tipo de operação baseado no tipo de movimento
     */
    public static TipoOperacao determinarOperacao(TipoMovimentoCaixa tipo) {
        return switch (tipo) {
            case PAGAMENTO_MESA, GORJETA, CONTAS_RECEBER, CAIXA_INICIAL, REFORCO ->
                    TipoOperacao.ENTRADA;
            case ESTORNO, CONTAS_PAGAR, SANGRIA ->
                    TipoOperacao.SAIDA;
            case OUTROS ->
                // Para OUTROS, será necessário especificar manualmente
                    null;
        };
    }
}
