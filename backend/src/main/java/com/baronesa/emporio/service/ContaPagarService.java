package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.ContaPagar;
import com.baronesa.emporio.entity.ContaPagarParcela;
import com.baronesa.emporio.entity.Fornecedor;
import com.baronesa.emporio.entity.CategoriaDespesa;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.enums.TipoMovimentoCaixa;
import com.baronesa.emporio.enums.TipoOperacao;
import com.baronesa.emporio.repository.ContaPagarRepository;
import com.baronesa.emporio.repository.ContaPagarParcelaRepository;
import com.baronesa.emporio.repository.FornecedorRepository;
import com.baronesa.emporio.repository.CategoriaDespesaRepository;
import com.baronesa.emporio.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContaPagarService {

    private final ContaPagarRepository contaPagarRepository;
    private final ContaPagarParcelaRepository parcelaRepository;
    private final FornecedorRepository fornecedorRepository;
    private final CategoriaDespesaRepository categoriaDespesaRepository;
    private final MovimentoCaixaService movimentoCaixaService;
    private final SecurityUtils securityUtils;

    public ContaPagarDTO criar(ContaPagarRequest request) {
        // Validações
        Fornecedor fornecedor = fornecedorRepository.findById(request.fornecedorId())
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));

        CategoriaDespesa categoria = categoriaDespesaRepository.findById(request.categoriaDespesaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

        // Cria a conta
        ContaPagar conta = ContaPagar.builder()
                .fornecedor(fornecedor)
                .categoriaDespesa(categoria)
                .descricao(request.descricao())
                .valorTotal(request.valorTotal())
                .numeroParcelas(request.numeroParcelas())
                .recorrente(request.recorrente())
                .build();

        // Adiciona as parcelas
        if (request.parcelas() != null && !request.parcelas().isEmpty()) {
            for (ContaPagarParcelaRequest parcelaReq : request.parcelas()) {
                ContaPagarParcela parcela = ContaPagarParcela.builder()
                        .numeroParcela(parcelaReq.numeroParcela())
                        .valor(parcelaReq.valor())
                        .dataVencimento(parcelaReq.dataVencimento())
                        .dataPagamento(parcelaReq.dataPagamento())
                        .formaPagamento(parcelaReq.formaPagamento())
                        .paga(parcelaReq.paga())
                        .build();
                conta.adicionarParcela(parcela);
            }
        }

        conta = contaPagarRepository.save(conta);
        return toDTO(conta);
    }

    public ContaPagarDTO editar(Long id, ContaPagarRequest request) {
        ContaPagar conta = contaPagarRepository.findByIdWithParcelas(id);
        if (conta == null) {
            throw new RuntimeException("Conta não encontrada");
        }

        // Atualiza dados básicos
        Fornecedor fornecedor = fornecedorRepository.findById(request.fornecedorId())
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));

        CategoriaDespesa categoria = categoriaDespesaRepository.findById(request.categoriaDespesaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

        conta.setFornecedor(fornecedor);
        conta.setCategoriaDespesa(categoria);
        conta.setDescricao(request.descricao());
        conta.setValorTotal(request.valorTotal());
        conta.setNumeroParcelas(request.numeroParcelas());
        conta.setRecorrente(request.recorrente());

        // Atualiza parcelas
        if (request.parcelas() != null) {
            // Criar um mapa das parcelas existentes por número
            Map<Integer, ContaPagarParcela> parcelasExistentes = conta.getParcelas().stream()
                    .collect(Collectors.toMap(ContaPagarParcela::getNumeroParcela, p -> p));

            // Limpar lista atual de parcelas
            conta.getParcelas().clear();

            // Processar cada parcela da requisição
            for (ContaPagarParcelaRequest parcelaReq : request.parcelas()) {
                ContaPagarParcela parcelaExistente = parcelasExistentes.get(parcelaReq.numeroParcela());

                if (parcelaExistente != null) {
                    // Atualiza parcela existente mantendo o ID
                    parcelaExistente.setValor(parcelaReq.valor());
                    parcelaExistente.setDataVencimento(parcelaReq.dataVencimento());

                    // Atualiza informações de pagamento se fornecidas
                    if (parcelaReq.dataPagamento() != null) {
                        parcelaExistente.setDataPagamento(parcelaReq.dataPagamento());
                        parcelaExistente.setFormaPagamento(parcelaReq.formaPagamento());
                        parcelaExistente.setPaga(parcelaReq.paga());
                    } else {
                        parcelaExistente.setDataPagamento(null);
                        parcelaExistente.setFormaPagamento(null);
                        parcelaExistente.setPaga(false);
                    }

                    conta.adicionarParcela(parcelaExistente);
                } else {
                    // Cria nova parcela
                    ContaPagarParcela novaParcela = ContaPagarParcela.builder()
                            .numeroParcela(parcelaReq.numeroParcela())
                            .valor(parcelaReq.valor())
                            .dataVencimento(parcelaReq.dataVencimento())
                            .dataPagamento(parcelaReq.dataPagamento())
                            .formaPagamento(parcelaReq.formaPagamento())
                            .paga(parcelaReq.paga())
                            .build();
                    conta.adicionarParcela(novaParcela);
                }
            }
        }

        conta = contaPagarRepository.save(conta);
        return toDTO(conta);
    }

    public void deletar(Long id) {
        ContaPagar conta = contaPagarRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        // Verifica se tem parcelas pagas
        if (conta.getParcelas().stream().anyMatch(ContaPagarParcela::isPaga)) {
            throw new RuntimeException("Não é possível excluir conta com parcelas pagas");
        }

        contaPagarRepository.deleteById(id);
    }

    public ContaPagarDTO buscarPorId(Long id) {
        ContaPagar conta = contaPagarRepository.findByIdWithParcelas(id);
        if (conta == null) {
            throw new RuntimeException("Conta não encontrada");
        }
        return toDTO(conta);
    }

    @Transactional
    public void pagarParcela(Long parcelaId, LocalDate dataPagamento, String formaPagamento) {
        log.info("Pagando parcela {} - Data: {}, Forma: {}", parcelaId, dataPagamento, formaPagamento);

        ContaPagarParcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        if (parcela.isPaga()) {
            throw new RuntimeException("Parcela já foi paga");
        }

        // Obter a conta associada
        ContaPagar conta = parcela.getContaPagar();

        // Marcar parcela como paga
        parcela.setDataPagamento(dataPagamento);
        parcela.setFormaPagamento(formaPagamento);
        parcela.setPaga(true);

        parcelaRepository.save(parcela);

        // Registrar movimento de caixa
        Usuario responsavel = securityUtils.getUsuarioAtual();

        // Converter forma de pagamento para enum TipoFormaPagamento
        TipoFormaPagamento tipoFormaPagamento = converterFormaPagamento(formaPagamento);

        // Criar observação com detalhes da conta
        String observacao = String.format("Pagamento parcela %d/%d - %s - %s",
                parcela.getNumeroParcela(),
                conta.getNumeroParcelas(),
                conta.getFornecedor().getRazaoSocial(),
                conta.getDescricao());

        movimentoCaixaService.registrar(
                TipoMovimentoCaixa.CONTAS_PAGAR,
                parcela.getValor(),
                tipoFormaPagamento,
                true,
                "CONTA_PAGAR",
                conta.getId(),
                responsavel,
                TipoOperacao.SAIDA,
                observacao
        );

        log.info("Parcela {} paga e movimento de caixa registrado", parcelaId);
    }

    @Transactional
    public void cancelarPagamentoParcela(Long parcelaId) {
        log.info("Cancelando pagamento da parcela {}", parcelaId);

        ContaPagarParcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        if (!parcela.isPaga()) {
            throw new RuntimeException("Parcela não está paga");
        }

        // Obter a conta associada
        ContaPagar conta = parcela.getContaPagar();

        // Converter forma de pagamento
        TipoFormaPagamento tipoFormaPagamento = converterFormaPagamento(parcela.getFormaPagamento());

        // Registrar estorno no movimento de caixa (antes de desmarcar o pagamento)
        Usuario responsavel = securityUtils.getUsuarioAtual();

        String observacao = String.format("Estorno pagamento parcela %d/%d - %s - %s",
                parcela.getNumeroParcela(),
                conta.getNumeroParcelas(),
                conta.getFornecedor().getRazaoSocial(),
                conta.getDescricao());

        // Para estorno de conta pagar, registrar como entrada (devolução)
        movimentoCaixaService.registrar(
                TipoMovimentoCaixa.ESTORNO,
                parcela.getValor(),
                tipoFormaPagamento,
                true,
                "CONTA_PAGAR",
                conta.getId(),
                responsavel,
                TipoOperacao.ENTRADA,
                observacao
        );

        // Desmarcar parcela como paga
        parcela.setDataPagamento(null);
        parcela.setFormaPagamento(null);
        parcela.setPaga(false);

        parcelaRepository.save(parcela);

        log.info("Pagamento da parcela {} cancelado e estorno registrado", parcelaId);
    }

    private ContaPagarDTO toDTO(ContaPagar conta) {
        List<ContaPagarParcelaDTO> parcelasDTO = conta.getParcelas().stream()
                .map(this::toParcelaDTO)
                .toList();

        return new ContaPagarDTO(
                conta.getId(),
                conta.getFornecedor().getId(),
                conta.getFornecedor().getRazaoSocial(),
                conta.getCategoriaDespesa().getId(),
                conta.getCategoriaDespesa().getNome(),
                conta.getDescricao(),
                conta.getValorTotal(),
                conta.getNumeroParcelas(),
                conta.isRecorrente(),
                conta.getDataCadastro(),
                conta.isQuitada(),
                conta.getValorPago(),
                conta.getValorPendente(),
                parcelasDTO
        );
    }

    private ContaPagarParcelaDTO toParcelaDTO(ContaPagarParcela parcela) {
        return new ContaPagarParcelaDTO(
                parcela.getId(),
                parcela.getNumeroParcela(),
                parcela.getValor(),
                parcela.getDataVencimento(),
                parcela.getDataPagamento(),
                parcela.getFormaPagamento(),
                parcela.isPaga(),
                parcela.isVencida()
        );
    }

    private TipoFormaPagamento converterFormaPagamento(String formaPagamento) {
        if (formaPagamento == null || formaPagamento.isEmpty()) {
            log.warn("Forma de pagamento não informada. Usando DINHEIRO como padrão.");
            return TipoFormaPagamento.DINHEIRO;
        }

        try {
            // Tentar converter diretamente para o enum
            return TipoFormaPagamento.valueOf(formaPagamento.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            // Mapear valores do frontend para enum
            return switch (formaPagamento.toUpperCase()) {
                case "PIX" -> TipoFormaPagamento.PIX;
                case "BOLETO", "TRANSFERENCIA" -> TipoFormaPagamento.TRANSFERENCIA;
                case "CARTAO", "CARTÃO" -> TipoFormaPagamento.CARTAO_CREDITO;
                case "DINHEIRO" -> TipoFormaPagamento.DINHEIRO;
                default -> {
                    log.warn("Forma de pagamento não reconhecida: {}. Usando OUTROS como padrão.", formaPagamento);
                    yield TipoFormaPagamento.OUTROS;
                }
            };
        }
    }
}
