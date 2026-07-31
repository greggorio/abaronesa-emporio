package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.ContaReceber;
import com.baronesa.emporio.entity.ContaReceberParcela;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.entity.Usuario.Role;
import com.baronesa.emporio.entity.TipoReceita;
import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.enums.TipoMovimentoCaixa;
import com.baronesa.emporio.enums.TipoOperacao;
import com.baronesa.emporio.repository.ContaReceberRepository;
import com.baronesa.emporio.repository.ContaReceberParcelaRepository;
import com.baronesa.emporio.repository.ClienteRepository;
import com.baronesa.emporio.repository.TipoReceitaRepository;
import com.baronesa.emporio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContaReceberService {

    private final ContaReceberRepository contaReceberRepository;
    private final ContaReceberParcelaRepository parcelaRepository;
    private final ClienteRepository clienteRepository;
    private final TipoReceitaRepository tipoReceitaRepository;
    private final MovimentoCaixaService movimentoCaixaService;
    private final UsuarioRepository usuarioRepository;
    private final com.baronesa.emporio.repository.SessaoConvidadoRepository sessaoConvidadoRepository;

    private static final Set<Role> ROLES_CLIENTE_ELEGIVEL = Set.of(
            Role.CLIENTE,
            Role.ADMIN,
            Role.FUNCIONARIO,
            Role.WAITER,
            Role.KDS,
            Role.CAIXA
    );

    public ContaReceberDTO criar(ContaReceberRequest request) {
        // Validações
        Usuario cliente = buscarClienteElegivel(request.clienteId());

        if (request.tipoReceitaId() == null) {
            throw new RuntimeException("Tipo de receita é obrigatório");
        }

        TipoReceita tipoReceita = tipoReceitaRepository.findById(request.tipoReceitaId())
                .orElseThrow(() -> new RuntimeException("Tipo de receita não encontrado"));

        // Cria a conta
        ContaReceber conta = ContaReceber.builder()
                .cliente(cliente)
                .tipoReceita(tipoReceita)
                .numeroDocumento(request.numeroDocumento())
                .descricao(request.descricao())
                .valorTotal(request.valorTotal())
                .numeroParcelas(request.numeroParcelas())
                .observacoes(request.observacoes())
                .recorrente(request.recorrente())
                .build();

        // Adiciona as parcelas
        if (request.parcelas() != null && !request.parcelas().isEmpty()) {
            for (ContaReceberParcelaRequest parcelaReq : request.parcelas()) {
                ContaReceberParcela parcela = ContaReceberParcela.builder()
                        .numeroParcela(parcelaReq.numeroParcela())
                        .valor(parcelaReq.valor())
                        .dataVencimento(parcelaReq.dataVencimento())
                        .dataRecebimento(parcelaReq.dataRecebimento())
                        .valorMulta(parcelaReq.valorMulta())
                        .valorJuros(parcelaReq.valorJuros())
                        .valorDesconto(parcelaReq.valorDesconto())
                        .valorRecebido(parcelaReq.valorRecebido())
                        .formaRecebimento(parcelaReq.formaRecebimento())
                        .recebida(parcelaReq.recebida())
                        .cobrancaEnviada(parcelaReq.cobrancaEnviada())
                        .dataEnvioCobranca(parcelaReq.dataEnvioCobranca())
                        .build();
                conta.adicionarParcela(parcela);
            }
        }

        conta = contaReceberRepository.save(conta);
        return toDTO(conta);
    }

    public ContaReceberDTO editar(Long id, ContaReceberRequest request) {
        ContaReceber conta = contaReceberRepository.findByIdWithParcelas(id);
        if (conta == null) {
            throw new RuntimeException("Conta não encontrada");
        }

        // Atualiza dados básicos
        Usuario cliente = buscarClienteElegivel(request.clienteId());

        if (request.tipoReceitaId() == null) {
            throw new RuntimeException("Tipo de receita é obrigatório");
        }

        TipoReceita tipoReceita = tipoReceitaRepository.findById(request.tipoReceitaId())
                .orElseThrow(() -> new RuntimeException("Tipo de receita não encontrado"));

        conta.setCliente(cliente);
        conta.setTipoReceita(tipoReceita);
        conta.setNumeroDocumento(request.numeroDocumento());
        conta.setDescricao(request.descricao());
        conta.setValorTotal(request.valorTotal());
        conta.setNumeroParcelas(request.numeroParcelas());
        conta.setObservacoes(request.observacoes());
        conta.setRecorrente(request.recorrente());

        // Atualiza parcelas
        if (request.parcelas() != null) {
            // Criar um mapa das parcelas existentes por número
            Map<Integer, ContaReceberParcela> parcelasExistentes = conta.getParcelas().stream()
                    .collect(Collectors.toMap(ContaReceberParcela::getNumeroParcela, p -> p));

            // Processar cada parcela da requisição e detectar mudanças ANTES de limpar
            for (ContaReceberParcelaRequest parcelaReq : request.parcelas()) {
                ContaReceberParcela parcelaExistente = parcelasExistentes.get(parcelaReq.numeroParcela());

                if (parcelaExistente != null) {
                    // Detectar mudança de status de recebimento ANTES de atualizar
                    boolean eraRecebida = parcelaExistente.isRecebida();
                    boolean agoraRecebida = parcelaReq.recebida();

                    // Atualiza parcela existente mantendo o ID
                    parcelaExistente.setValor(parcelaReq.valor());
                    parcelaExistente.setDataVencimento(parcelaReq.dataVencimento());
                    parcelaExistente.setDataRecebimento(parcelaReq.dataRecebimento());
                    parcelaExistente.setValorMulta(parcelaReq.valorMulta());
                    parcelaExistente.setValorJuros(parcelaReq.valorJuros());
                    parcelaExistente.setValorDesconto(parcelaReq.valorDesconto());
                    parcelaExistente.setValorRecebido(parcelaReq.valorRecebido());
                    parcelaExistente.setFormaRecebimento(parcelaReq.formaRecebimento());
                    parcelaExistente.setRecebida(parcelaReq.recebida());
                    parcelaExistente.setCobrancaEnviada(parcelaReq.cobrancaEnviada());
                    parcelaExistente.setDataEnvioCobranca(parcelaReq.dataEnvioCobranca());

                    // Registrar movimento de caixa se mudou o status de recebimento
                    if (!eraRecebida && agoraRecebida && parcelaReq.dataRecebimento() != null && parcelaReq.formaRecebimento() != null) {
                        // Parcela foi marcada como recebida agora - registrar entrada
                        registrarMovimentoRecebimento(parcelaExistente, conta);
                    } else if (eraRecebida && !agoraRecebida) {
                        // Parcela foi desmarcada como recebida - registrar estorno
                        registrarEstornoRecebimento(parcelaExistente, conta);
                    }
                }
            }

            // Agora sim, limpar e reconstruir a lista
            conta.getParcelas().clear();

            // Re-adicionar as parcelas atualizadas
            for (ContaReceberParcelaRequest parcelaReq : request.parcelas()) {
                ContaReceberParcela parcelaExistente = parcelasExistentes.get(parcelaReq.numeroParcela());

                if (parcelaExistente != null) {
                    conta.adicionarParcela(parcelaExistente);
                } else {
                    // Cria nova parcela
                    ContaReceberParcela novaParcela = ContaReceberParcela.builder()
                            .numeroParcela(parcelaReq.numeroParcela())
                            .valor(parcelaReq.valor())
                            .dataVencimento(parcelaReq.dataVencimento())
                            .dataRecebimento(parcelaReq.dataRecebimento())
                            .valorMulta(parcelaReq.valorMulta())
                            .valorJuros(parcelaReq.valorJuros())
                            .valorDesconto(parcelaReq.valorDesconto())
                            .valorRecebido(parcelaReq.valorRecebido())
                            .formaRecebimento(parcelaReq.formaRecebimento())
                            .recebida(parcelaReq.recebida())
                            .cobrancaEnviada(parcelaReq.cobrancaEnviada())
                            .dataEnvioCobranca(parcelaReq.dataEnvioCobranca())
                            .build();
                    conta.adicionarParcela(novaParcela);
                }
            }
        }

        conta = contaReceberRepository.save(conta);
        return toDTO(conta);
    }

    /**
     * Busca usuário por ID validando se possui algum dos roles elegíveis para ser destinatário de Conta a Receber.
     */
    private Usuario buscarClienteElegivel(Long clienteId) {
        Usuario cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        boolean elegivel = cliente.getRoles() != null &&
                cliente.getRoles().stream().anyMatch(ROLES_CLIENTE_ELEGIVEL::contains);

        if (!elegivel) {
            throw new RuntimeException("Cliente não encontrado ou sem role elegível");
        }
        return cliente;
    }

    public void deletar(Long id) {
        ContaReceber conta = contaReceberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        // Verifica se tem parcelas recebidas
        if (conta.getParcelas().stream().anyMatch(ContaReceberParcela::isRecebida)) {
            throw new RuntimeException("Não é possível excluir conta com parcelas recebidas");
        }

        contaReceberRepository.deleteById(id);
    }

    public ContaReceberDTO buscarPorId(Long id) {
        ContaReceber conta = contaReceberRepository.findByIdWithParcelas(id);
        if (conta == null) {
            throw new RuntimeException("Conta não encontrada");
        }
        return toDTO(conta);
    }

    public List<RecebimentoHojeDTO> buscarRecebimentosHoje() {
        LocalDate hoje = LocalDate.now();

        // Buscar parcelas recebidas hoje
        List<ContaReceberParcela> parcelasRecebidasHoje = parcelaRepository.findByDataRecebimentoOuVencimento(hoje);

        // Converter para DTO
        return parcelasRecebidasHoje.stream()
                .map(parcela -> RecebimentoHojeDTO.builder()
                        .dataVencimento(parcela.getDataVencimento())
                        .dataRecebto(parcela.getDataRecebimento())
                        .nomeCliente(parcela.getContaReceber().getCliente().getNome())
                        .valor(parcela.getValorRecebido() != null ? parcela.getValorRecebido() : parcela.getValor())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void receberParcela(Long parcelaId, LocalDate dataRecebimento, String formaRecebimento,
                               BigDecimal valorRecebido) {
        log.info("Recebendo parcela {} - Data: {}, Forma: {}, Valor: {}",
                parcelaId, dataRecebimento, formaRecebimento, valorRecebido);

        ContaReceberParcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        if (parcela.isRecebida()) {
            throw new RuntimeException("Parcela já foi recebida");
        }

        // Obter a conta associada
        ContaReceber conta = parcela.getContaReceber();

        // Marcar parcela como recebida
        parcela.setDataRecebimento(dataRecebimento);
        parcela.setFormaRecebimento(formaRecebimento);
        parcela.setValorRecebido(valorRecebido);
        parcela.setRecebida(true);

        parcelaRepository.save(parcela);

        // Registrar movimento de caixa
        Usuario responsavel = obterUsuarioLogado();

        // Converter forma de recebimento para enum TipoFormaPagamento
        TipoFormaPagamento tipoFormaPagamento = converterFormaPagamento(formaRecebimento);

        // Criar observação com detalhes da conta
        String observacao = String.format("Recebimento parcela %d/%d - %s - %s",
                parcela.getNumeroParcela(),
                conta.getNumeroParcelas(),
                conta.getCliente().getNome(),
                conta.getDescricao());

        // Se tem juros ou multa, adicionar na observação
        if (parcela.getValorJuros() != null && parcela.getValorJuros().compareTo(BigDecimal.ZERO) > 0) {
            observacao += String.format(" (Juros: R$ %.2f)", parcela.getValorJuros());
        }
        if (parcela.getValorMulta() != null && parcela.getValorMulta().compareTo(BigDecimal.ZERO) > 0) {
            observacao += String.format(" (Multa: R$ %.2f)", parcela.getValorMulta());
        }
        if (parcela.getValorDesconto() != null && parcela.getValorDesconto().compareTo(BigDecimal.ZERO) > 0) {
            observacao += String.format(" (Desconto: R$ %.2f)", parcela.getValorDesconto());
        }

        movimentoCaixaService.registrarMovimentoConta(
                TipoMovimentoCaixa.CONTAS_RECEBER,
                valorRecebido, // Usar o valor efetivamente recebido
                tipoFormaPagamento,
                conta.getId(),
                "CONTA_RECEBER",
                responsavel
        );

        log.info("Parcela {} recebida e movimento de caixa registrado", parcelaId);
    }

    @Transactional
    public void cancelarRecebimentoParcela(Long parcelaId, String motivoCancelamento) {
        log.info("Cancelando recebimento da parcela {} - Motivo: {}", parcelaId, motivoCancelamento);

        ContaReceberParcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        if (!parcela.isRecebida()) {
            throw new RuntimeException("Parcela não está recebida");
        }

        // Obter a conta associada
        ContaReceber conta = parcela.getContaReceber();

        // Converter forma de recebimento
        TipoFormaPagamento tipoFormaPagamento = converterFormaPagamento(parcela.getFormaRecebimento());

        // Registrar estorno no movimento de caixa
        Usuario responsavel = obterUsuarioLogado();

        String observacao = String.format("Estorno recebimento parcela %d/%d - %s - Motivo: %s",
                parcela.getNumeroParcela(),
                conta.getNumeroParcelas(),
                conta.getCliente().getNome(),
                motivoCancelamento);

        // Para estorno de conta receber, registrar como saída (devolução)
        movimentoCaixaService.registrar(
                TipoMovimentoCaixa.OUTROS,
                parcela.getValorRecebido(),
                tipoFormaPagamento,
                true,
                "CONTA_RECEBER",
                conta.getId(),
                responsavel,
                TipoOperacao.SAIDA,
                observacao
        );

        // Desmarcar parcela como recebida
        parcela.setDataRecebimento(null);
        parcela.setFormaRecebimento(null);
        parcela.setValorRecebido(null);
        parcela.setRecebida(false);

        parcelaRepository.save(parcela);

        log.info("Recebimento da parcela {} cancelado e estorno registrado", parcelaId);
    }

    @Transactional
    public void receberParcelaCredito(Long vendaId, Long parcelaId, LocalDate dataRecebimento,
                                      String formaRecebimento, BigDecimal valorRecebido) {
        log.info("Recebendo parcela de crédito - Venda: {}, Parcela: {}", vendaId, parcelaId);

        // Esta é uma parcela de venda a prazo/crediário
        ContaReceberParcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        if (parcela.isRecebida()) {
            throw new RuntimeException("Parcela já foi recebida");
        }

        ContaReceber conta = parcela.getContaReceber();

        // Marcar parcela como recebida
        parcela.setDataRecebimento(dataRecebimento);
        parcela.setFormaRecebimento(formaRecebimento);
        parcela.setValorRecebido(valorRecebido);
        parcela.setRecebida(true);

        parcelaRepository.save(parcela);

        // Registrar movimento de caixa específico para crediário
        Usuario responsavel = obterUsuarioLogado();
        TipoFormaPagamento tipoFormaPagamento = converterFormaPagamento(formaRecebimento);

        String observacao = String.format("Recebimento crediário - Parcela %d/%d - Venda #%d - %s",
                parcela.getNumeroParcela(),
                conta.getNumeroParcelas(),
                vendaId,
                conta.getCliente().getNome());

        // Usar tipo específico ou criar um novo tipo RECEBIMENTO_CREDIARIO se necessário
        movimentoCaixaService.registrar(
                TipoMovimentoCaixa.CONTAS_RECEBER,
                valorRecebido,
                tipoFormaPagamento,
                true,
                "VENDA_CREDIARIO",
                vendaId,
                responsavel,
                TipoOperacao.ENTRADA,
                observacao
        );

        log.info("Parcela de crediário {} recebida e movimento de caixa registrado", parcelaId);
    }

    public void marcarCobrancaEnviada(Long parcelaId) {
        ContaReceberParcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        parcela.setCobrancaEnviada(true);
        parcela.setDataEnvioCobranca(LocalDate.now());

        parcelaRepository.save(parcela);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buscarMinhasContas() {
        Usuario usuario = obterUsuarioLogado();
        
        List<ContaReceber> contas = contaReceberRepository.findByClienteId(usuario.getId());
        
        List<ContaReceberDTO> todas = contas.stream()
                .map(this::toDTO)
                .toList();

        // Abertas: Não quitadas
        List<ContaReceberDTO> abertas = todas.stream()
                .filter(c -> !c.quitada())
                .sorted((a, b) -> {
                     // Ordenar por vencimento da primeira parcela pendente
                     LocalDate vA = a.parcelas().stream().filter(p -> !p.recebida()).map(com.baronesa.emporio.dto.ContaReceberParcelaDTO::dataVencimento).min(java.util.Comparator.naturalOrder()).orElse(LocalDate.MAX);
                     LocalDate vB = b.parcelas().stream().filter(p -> !p.recebida()).map(com.baronesa.emporio.dto.ContaReceberParcelaDTO::dataVencimento).min(java.util.Comparator.naturalOrder()).orElse(LocalDate.MAX);
                     return vA.compareTo(vB);
                })
                .collect(Collectors.toList());

        // Fechadas: Quitadas
        List<ContaReceberDTO> fechadas = todas.stream()
                .filter(ContaReceberDTO::quitada)
                .sorted((a, b) -> b.dataCadastro().compareTo(a.dataCadastro()))
                .collect(Collectors.toList());
                
        BigDecimal totalAberto = abertas.stream()
                .map(ContaReceberDTO::valorPendente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long visitas = sessaoConvidadoRepository.countByUsuario_IdAndEntrouEmAfter(usuario.getId(), java.time.LocalDateTime.now().minusMonths(3));

        return Map.of(
            "clienteNome", usuario.getNome(),
            "clienteDesde", usuario.getCriadoEm(),
            "totalVisitasTrimestre", visitas,
            "resumo", Map.of("totalAberto", totalAberto),
            "faturasAbertas", abertas,
            "historico", fechadas
        );
    }

    private Usuario obterUsuarioLogado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário logado não encontrado"));
    }

    private TipoFormaPagamento converterFormaPagamento(String formaPagamento) {
        try {
            return TipoFormaPagamento.valueOf(formaPagamento.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Forma de pagamento não reconhecida: {}. Usando DINHEIRO como padrão.", formaPagamento);
            return TipoFormaPagamento.DINHEIRO;
        }
    }

    private void registrarMovimentoRecebimento(ContaReceberParcela parcela, ContaReceber conta) {
        Usuario responsavel = obterUsuarioLogado();
        TipoFormaPagamento tipoFormaPagamento = converterFormaPagamento(parcela.getFormaRecebimento());

        String observacao = String.format("Recebimento parcela %d/%d - %s - %s",
                parcela.getNumeroParcela(),
                conta.getNumeroParcelas(),
                conta.getCliente().getNome(),
                conta.getDescricao());

        // Se tem juros ou multa, adicionar na observação
        if (parcela.getValorJuros() != null && parcela.getValorJuros().compareTo(BigDecimal.ZERO) > 0) {
            observacao += String.format(" (Juros: R$ %.2f)", parcela.getValorJuros());
        }
        if (parcela.getValorMulta() != null && parcela.getValorMulta().compareTo(BigDecimal.ZERO) > 0) {
            observacao += String.format(" (Multa: R$ %.2f)", parcela.getValorMulta());
        }
        if (parcela.getValorDesconto() != null && parcela.getValorDesconto().compareTo(BigDecimal.ZERO) > 0) {
            observacao += String.format(" (Desconto: R$ %.2f)", parcela.getValorDesconto());
        }

        BigDecimal valorRecebido = parcela.getValorRecebido() != null ? parcela.getValorRecebido() : parcela.getValor();

        movimentoCaixaService.registrar(
                TipoMovimentoCaixa.CONTAS_RECEBER,
                valorRecebido,
                tipoFormaPagamento,
                true,
                "CONTA_RECEBER",
                conta.getId(),
                responsavel,
                TipoOperacao.ENTRADA,
                observacao
        );

        log.info("Movimento de caixa registrado para recebimento da parcela {} - Valor: {}",
                parcela.getId(), valorRecebido);
    }

    private void registrarEstornoRecebimento(ContaReceberParcela parcela, ContaReceber conta) {
        Usuario responsavel = obterUsuarioLogado();
        TipoFormaPagamento tipoFormaPagamento = converterFormaPagamento(parcela.getFormaRecebimento());

        String observacao = String.format("Estorno recebimento parcela %d/%d - %s - %s",
                parcela.getNumeroParcela(),
                conta.getNumeroParcelas(),
                conta.getCliente().getNome(),
                conta.getDescricao());

        BigDecimal valorRecebido = parcela.getValorRecebido() != null ? parcela.getValorRecebido() : parcela.getValor();

        // Para estorno de conta receber, registrar como saída (devolução)
        movimentoCaixaService.registrar(
                TipoMovimentoCaixa.ESTORNO,
                valorRecebido,
                tipoFormaPagamento,
                true,
                "CONTA_RECEBER",
                conta.getId(),
                responsavel,
                TipoOperacao.SAIDA,
                observacao
        );

        log.info("Estorno de movimento de caixa registrado para parcela {} - Valor: {}",
                parcela.getId(), valorRecebido);
    }

    private ContaReceberDTO toDTO(ContaReceber conta) {
        List<ContaReceberParcelaDTO> parcelasDTO = conta.getParcelas().stream()
                .map(this::toParcelaDTO)
                .toList();

        return new ContaReceberDTO(
                conta.getId(),
                conta.getCliente().getId(),
                conta.getCliente().getNome(),
                conta.getTipoReceita().getId(),
                conta.getTipoReceita().getNome(),
                conta.getNumeroDocumento(),
                conta.getDescricao(),
                conta.getValorTotal(),
                conta.getNumeroParcelas(),
                conta.getObservacoes(),
                conta.isRecorrente(),
                conta.getDataCadastro(),
                conta.isQuitada(),
                conta.getValorRecebido(),
                conta.getValorPendente(),
                parcelasDTO
        );
    }

    private ContaReceberParcelaDTO toParcelaDTO(ContaReceberParcela parcela) {
        return new ContaReceberParcelaDTO(
                parcela.getId(),
                parcela.getNumeroParcela(),
                parcela.getValor(),
                parcela.getDataVencimento(),
                parcela.getDataRecebimento(),
                parcela.getValorMulta(),
                parcela.getValorJuros(),
                parcela.getValorDesconto(),
                parcela.getValorRecebido(),
                parcela.getFormaRecebimento(),
                parcela.isRecebida(),
                parcela.isVencida(),
                parcela.getDiasAtraso(),
                parcela.isCobrancaEnviada(),
                parcela.getDataEnvioCobranca()
        );
    }
}
