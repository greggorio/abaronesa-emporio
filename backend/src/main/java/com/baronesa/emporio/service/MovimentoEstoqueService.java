package com.baronesa.emporio.service;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.repository.*;
import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.enums.TipoMovimentoEstoque;
import com.baronesa.emporio.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovimentoEstoqueService {

    private final MovimentoEstoqueRepository movimentoRepository;
    private final EstoqueRepository estoqueRepository;
    private final ProdutoSKURepository skuRepository;
    private final MessageResolver messageResolver;
    private final ErpNotificationService erpNotificationService;
    private final SecurityUtils securityUtils;
    private final EstoqueProdutoRepository estoqueProdutoRepository;
    private final EmbalagemRepository embalagemRepository;
    private final EstoqueLoteRepository estoqueLoteRepository;
    private final MovimentoEstoqueLoteRepository movimentoEstoqueLoteRepository;

    @Transactional
    public MovimentoEstoqueDTO movimentarEstoque(MovimentoEstoqueRequest request) {
        // Buscar usuário logado se não for um movimento automático do sistema
        Usuario usuarioLogado = null;
        try {
            usuarioLogado = securityUtils.getUsuarioAtual();
        } catch (Exception e) {
            log.warn("Não foi possível obter usuário logado: {}", e.getMessage());
        }

        // Buscar SKU e seu estoque (com produto carregado para verificar se é insumo)
        ProdutoSKU sku = skuRepository.findByIdWithProduto(request.getSkuId())
                .orElseThrow(() -> new RuntimeException(messageResolver.getMessage("movimento.estoque.error.sku-not-found")));

        Estoque estoque = sku.getEstoque();
        if (estoque == null) {
            // Criar estoque se não existir (persistindo antes de associar para garantir ID)
            estoque = estoqueRepository.save(criarEstoqueVazio());
            sku.setEstoque(estoque);
            skuRepository.save(sku);
        } else if (estoque.getId() == null) {
            // Correção defensiva: garantir que o estoque tenha ID antes de prosseguir
            estoque = estoqueRepository.save(estoque);
            sku.setEstoque(estoque);
        }

        // Calcular novo estoque
        int quantidadeAtualInt = estoque.getQuantidade() != null ? estoque.getQuantidade() : 0;
        BigDecimal quantidadeAtual = BigDecimal.valueOf(quantidadeAtualInt);
        BigDecimal quantidadeMovimento = request.getQuantidade();
        BigDecimal novoEstoque = calcularNovoEstoque(
                quantidadeAtual,
                quantidadeMovimento,
                TipoMovimentoEstoque.fromCodigo(request.getTipoMovimento())
        );

        // Validar estoque negativo se não permitido
        if (novoEstoque.compareTo(BigDecimal.ZERO) < 0 && !podeEstoqueNegativo(sku)) {
            throw new RuntimeException(messageResolver.getMessage("movimento.estoque.error.estoque-insuficiente"));
        }

        // Preparar cálculo em unidade base (para produtos INSUMO)
        Integer fatorEfetivo = null;
        if (request.getEmbalagemId() != null) {
            Embalagem emb = embalagemRepository.findById(request.getEmbalagemId())
                    .orElseThrow(() -> new RuntimeException("Embalagem não encontrada"));
            if (!emb.getProduto().getId().equals(sku.getProduto().getId())) {
                throw new RuntimeException("Embalagem selecionada não pertence ao produto do SKU");
            }
            fatorEfetivo = emb.getFatorBase();
        } else if (sku.getEmbalagem() != null && sku.getEmbalagem().getFatorBase() != null && sku.getEmbalagem().getFatorBase() > 0) {
            fatorEfetivo = sku.getEmbalagem().getFatorBase();
        } else if (sku.getProduto() != null && sku.getProduto().getEmbalagens() != null) {
            for (Embalagem e : sku.getProduto().getEmbalagens()) {
                if (Boolean.TRUE.equals(e.getPrincipal()) && e.getFatorBase() != null && e.getFatorBase() > 0) {
                    fatorEfetivo = e.getFatorBase();
                    break;
                }
            }
        }
        if (fatorEfetivo == null || fatorEfetivo <= 0) {
            fatorEfetivo = 1; // fallback seguro
        }

        Integer quantidadeBase = null;
        Integer estoqueAnteriorBase = null;
        Integer estoqueAtualBase = null;

        if (Boolean.TRUE.equals(sku.getProduto().getInsumo())) {
            // Delta em base conforme tipo de movimento
            int unidades = quantidadeMovimento.intValue();
            int deltaBase;
            switch (TipoMovimentoEstoque.fromCodigo(request.getTipoMovimento())) {
                case VENDA, CONSIGNACAO, ESTORNO_ENTRADA, ZERAR_ESTOQUE, CONSUMO_PRODUCAO -> deltaBase = -Math.abs(unidades) * fatorEfetivo;
                default -> deltaBase = unidades * fatorEfetivo;
            }

            // Estoque base anterior e atual a partir de EstoqueProduto
            EstoqueProduto epAtual = estoqueProdutoRepository.findByProdutoId(sku.getProduto().getId())
                    .orElseGet(() -> EstoqueProduto.builder()
                            .produto(sku.getProduto())
                            .quantidadeBase(0)
                            .reservadoBase(0)
                            .estoqueMinimoBase(0)
                            .build());
            int baseAntes = epAtual.getQuantidadeBase() == null ? 0 : epAtual.getQuantidadeBase();
            int baseDepois = baseAntes + deltaBase;

            quantidadeBase = Math.abs(unidades) * fatorEfetivo * (unidades < 0 ? -1 : 1);
            // Para apresentação, manteremos sinal apenas na quantidade; anteriores/atuais são saldos absolutos
            estoqueAnteriorBase = baseAntes;
            estoqueAtualBase = baseDepois;
        }

        // Criar movimento com dados de exibição corretos
        MovimentoEstoque movimento = MovimentoEstoque.builder()
                .sku(sku)
                .produto(sku.getProduto())
                .tipoMovimento(request.getTipoMovimento())
                .quantidade(quantidadeMovimento)
                .estoqueAnterior(quantidadeAtual)
                .estoqueAtual(novoEstoque)
                .quantidadeBase(quantidadeBase)
                .estoqueAnteriorBase(estoqueAnteriorBase)
                .estoqueAtualBase(estoqueAtualBase)
                .observacao(request.getObservacao())
                .vendaId(request.getVendaId())
                .recebimentoId(request.getRecebimentoId())
                .consignacaoId(request.getConsignacaoId())
                .documentoReferencia(request.getDocumentoReferencia())
                .itemPedidoId(request.getItemPedidoId())
                .usuario(usuarioLogado)
                .build();

        // Atualizar estoque do SKU (legado)
        estoque.setQuantidade(novoEstoque.intValue());

        // Atualizar estoque centralizado do produto (unidade base) somente para produtos insumo
        if (sku.getProduto() != null && Boolean.TRUE.equals(sku.getProduto().getInsumo())) {
            try {
                int deltaBase = calcularDeltaBaseParaProduto(sku, quantidadeMovimento, TipoMovimentoEstoque.fromCodigo(request.getTipoMovimento()));
                // Se houver embalagem efetiva calculada acima, preferi-la
                if (fatorEfetivo != null && fatorEfetivo > 0) {
                    int unidades = quantidadeMovimento.intValue();
                    int overrideDelta = unidades * fatorEfetivo;
                    switch (TipoMovimentoEstoque.fromCodigo(request.getTipoMovimento())) {
                        case VENDA, CONSIGNACAO, ESTORNO_ENTRADA, ZERAR_ESTOQUE, CONSUMO_PRODUCAO -> deltaBase = -Math.abs(overrideDelta);
                        default -> deltaBase = overrideDelta;
                    }
                }
                if (deltaBase != 0) {
                    atualizarEstoqueProduto(sku.getProduto(), deltaBase);
                }
            } catch (Exception e) {
                log.error("Falha ao atualizar EstoqueProduto: {}", e.getMessage());
            }
        }

        // Salvar primeiro o movimento principal para garantir que tenha ID
        movimento = movimentoRepository.save(movimento);
        estoqueRepository.save(estoque);

        // Aplicar lógica FEFO para saídas de produtos com controle de validade
        TipoMovimentoEstoque tipoMovimento = TipoMovimentoEstoque.fromCodigo(request.getTipoMovimento());

        // Verificar se é uma saída e se o produto controla validade
        if (isTipoSaida(tipoMovimento) && sku.getProduto() != null && Boolean.TRUE.equals(sku.getProduto().getControlaValidade())) {
            aplicarFefo(sku, quantidadeMovimento, movimento);
        }

        // Se for um ajuste e tiver usuário logado, enviar notificação
        if (request.getTipoMovimento() == TipoMovimentoEstoque.AJUSTE.getCodigo() && usuarioLogado != null) {
            try {
                erpNotificationService.notifyStockAdjustment(
                        sku.getSku(),
                        formatarDescricaoSku(sku),
                        quantidadeMovimento,
                        quantidadeAtual,
                        novoEstoque,
                        request.getObservacao(),
                        usuarioLogado.getId()
                );
            } catch (Exception e) {
                log.error("Erro ao enviar notificação de ajuste de estoque: ", e);
            }
        }

        return toDTO(movimento);
    }

    private BigDecimal calcularNovoEstoque(BigDecimal atual, BigDecimal movimento, TipoMovimentoEstoque tipo) {
        switch (tipo) {
            case ENTRADA:
            case PRODUCAO:
            case ESTORNO_VENDA:
            case ESTORNO_CONSIGNACAO:
            case AJUSTE:  // ← MOVER PARA CÁ!
                return atual.add(movimento);

            case VENDA:
            case CONSIGNACAO:
            case ESTORNO_ENTRADA:
            case CONSUMO_PRODUCAO:
                return atual.subtract(movimento);

            case INVENTARIO:
                // Inventário pode ficar separado se tiver lógica especial
                return atual.add(movimento);  // ou subtract, depende da lógica de negócio

            case ZERAR_ESTOQUE:
                return BigDecimal.ZERO;

            case INICIAR_ESTOQUE:
                return movimento;

            default:
                return atual;
        }
    }

    private int calcularDeltaBaseParaProduto(ProdutoSKU sku, BigDecimal movimento, TipoMovimentoEstoque tipo) {
        int fator = 1;
        if (sku.getEmbalagem() != null && sku.getEmbalagem().getFatorBase() != null && sku.getEmbalagem().getFatorBase() > 0) {
            fator = sku.getEmbalagem().getFatorBase();
        } else if (sku.getProduto() != null && sku.getProduto().getEmbalagens() != null) {
            for (Embalagem e : sku.getProduto().getEmbalagens()) {
                if (Boolean.TRUE.equals(e.getPrincipal()) && e.getFatorBase() != null && e.getFatorBase() > 0) {
                    fator = e.getFatorBase();
                    break;
                }
            }
        }

        // Considera o sinal de acordo com o tipo
        int unidades = movimento.intValue(); // assumindo quantidades inteiras
        int delta = unidades * fator;

        switch (tipo) {
            case ENTRADA:
            case PRODUCAO:
            case ESTORNO_VENDA:
            case ESTORNO_CONSIGNACAO:
            case AJUSTE:
            case INICIAR_ESTOQUE:
            case INVENTARIO:
                return delta; // positivo ou negativo conforme 'movimento'
            case VENDA:
            case CONSIGNACAO:
            case ESTORNO_ENTRADA:
            case CONSUMO_PRODUCAO:
                return -delta;
            case ZERAR_ESTOQUE:
                // request costuma vir com quantidade atual do SKU; vamos subtrair o equivalente em base
                return -Math.abs(delta);
            default:
                return 0;
        }
    }

    private void atualizarEstoqueProduto(Produto produto, int deltaBase) {
        EstoqueProduto ep = estoqueProdutoRepository.findByProdutoId(produto.getId())
                .orElseGet(() -> EstoqueProduto.builder()
                        .produto(produto)
                        .quantidadeBase(0)
                        .reservadoBase(0)
                        .estoqueMinimoBase(0)
                        .build());
        int atual = ep.getQuantidadeBase() == null ? 0 : ep.getQuantidadeBase();
        ep.setQuantidadeBase(atual + deltaBase);
        estoqueProdutoRepository.save(ep);
    }

    @Transactional
    public MovimentoEstoqueDTO ajustarEstoque(Long skuId, Long embalagemId, BigDecimal quantidade, String motivo, boolean adicionar) {
        // Buscar usuário logado
        Usuario usuarioLogado = securityUtils.getUsuarioAtual();

        // Buscar SKU antes do ajuste para capturar dados necessários (com produto carregado)
        ProdutoSKU sku = skuRepository.findByIdWithProduto(skuId)
                .orElseThrow(() -> new RuntimeException(messageResolver.getMessage("movimento.estoque.error.sku-not-found")));

        Estoque estoqueAtual = sku.getEstoque();
        BigDecimal estoqueAnterior = estoqueAtual != null ?
                BigDecimal.valueOf(estoqueAtual.getQuantidade()) : BigDecimal.ZERO;

        // Criar request para movimento
        MovimentoEstoqueRequest request = MovimentoEstoqueRequest.builder()
                .skuId(skuId)
                .embalagemId(embalagemId)
                .tipoMovimento(TipoMovimentoEstoque.AJUSTE.getCodigo())
                .quantidade(adicionar ? quantidade : quantidade.negate())
                .observacao(motivo)
                .build();

        // Executar o movimento (isso vai atualizar o estoque)
        MovimentoEstoqueDTO movimentoDTO = movimentarEstoque(request);

        // Atualizar o movimento com o usuário que realizou o ajuste
        MovimentoEstoque movimento = movimentoRepository.findById(movimentoDTO.getId())
                .orElseThrow(() -> new RuntimeException("Movimento não encontrado após criação"));
        movimento.setUsuario(usuarioLogado);
        movimentoRepository.save(movimento);

        // Buscar o estoque atualizado
        BigDecimal estoqueNovo = BigDecimal.valueOf(sku.getEstoque().getQuantidade());

        // Enviar notificação sobre o ajuste
        try {
            erpNotificationService.notifyStockAdjustment(
                    sku.getSku(),
                    formatarDescricaoSku(sku),
                    adicionar ? quantidade : quantidade.negate(),
                    estoqueAnterior,
                    estoqueNovo,
                    motivo,
                    usuarioLogado.getId()
            );
            log.info("Notificação de ajuste de estoque enviada com sucesso para SKU: {}", sku.getSku());
        } catch (Exception e) {
            log.error("Erro ao enviar notificação de ajuste de estoque: ", e);
            // Não lançar exceção para não impedir o ajuste de estoque
        }

        return movimentoDTO;
    }

    private Estoque criarEstoqueVazio() {
        return Estoque.builder()
                .quantidade(0)
                .estoqueMinimo(0)
                .reservado(0)
                .build();
    }

    public MovimentoEstoqueDTO buscarPorId(Long id) {
        MovimentoEstoque movimento = movimentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(messageResolver.getMessage("movimento.estoque.error.not-found")));
        return toDTO(movimento);
    }

    public Page<MovimentoEstoqueDTO> listarPorSku(Long skuId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dataMovimento").descending());
        Page<MovimentoEstoque> movimentos = movimentoRepository.findBySkuIdOrderByDataMovimentoDesc(skuId, pageable);
        return movimentos.map(this::toDTO);
    }

    public Map<String, Object> gerarRelatorio(String dataInicio, String dataFim, Integer tipoMovimento) {
        LocalDateTime inicio = LocalDateTime.parse(dataInicio + "T00:00:00");
        LocalDateTime fim = LocalDateTime.parse(dataFim + "T23:59:59");

        List<MovimentoEstoque> movimentos;
        if (tipoMovimento != null) {
            movimentos = movimentoRepository.findByTipoMovimentoAndDataMovimentoBetween(tipoMovimento, inicio, fim);
        } else {
            movimentos = movimentoRepository.findMovimentosPeriodo(inicio, fim);
        }

        // Calcular estatísticas
        Map<String, Object> estatisticas = new HashMap<>();
        estatisticas.put("totalMovimentos", movimentos.size());
        estatisticas.put("periodo", Map.of("inicio", dataInicio, "fim", dataFim));

        // Agrupar por tipo
        Map<String, Long> porTipo = movimentos.stream()
                .collect(Collectors.groupingBy(
                        m -> TipoMovimentoEstoque.fromCodigo(m.getTipoMovimento()).getDescricao(),
                        Collectors.counting()
                ));
        estatisticas.put("movimentosPorTipo", porTipo);

        // Lista de movimentos
        List<MovimentoEstoqueDTO> movimentosDTO = movimentos.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return Map.of(
                "estatisticas", estatisticas,
                "movimentos", movimentosDTO
        );
    }

    private MovimentoEstoqueDTO toDTO(MovimentoEstoque movimento) {
        ProdutoSKU sku = movimento.getSku();
        Produto produto = sku.getProduto();

        return MovimentoEstoqueDTO.builder()
                .id(movimento.getId())
                .dataMovimento(movimento.getDataMovimento())
                .produtoNome(produto.getNome())
                .skuCodigo(sku.getSku())
                .skuDescricao(formatarDescricaoSku(sku))
                .tipoMovimento(TipoMovimentoEstoque.fromCodigo(movimento.getTipoMovimento()).getDescricao())
                .quantidade(movimento.getQuantidade())
                .estoqueAnterior(movimento.getEstoqueAnterior())
                .estoqueAtual(movimento.getEstoqueAtual())
                .observacao(movimento.getObservacao())
                .documentoReferencia(movimento.getDocumentoReferencia())
                .itemPedidoId(movimento.getItemPedidoId())
                .build();
    }

    private String formatarDescricaoSku(ProdutoSKU sku) {
        StringBuilder desc = new StringBuilder(sku.getProduto().getNome());
        if (sku.getVariacao() != null && !sku.getVariacao().isBlank()) {
            desc.append(" - ").append(sku.getVariacao());
        }
        return desc.toString();
    }

    private boolean podeEstoqueNegativo(ProdutoSKU sku) {
        // TODO: Implementar lógica para verificar se o grupo do produto permite estoque negativo
        // Por enquanto, permitir para todos
        return true;
    }

    private boolean isTipoSaida(TipoMovimentoEstoque tipo) {
        // Tipos de movimento que representam saída de estoque
        return tipo == TipoMovimentoEstoque.VENDA ||
               tipo == TipoMovimentoEstoque.CONSUMO_PRODUCAO ||
               tipo == TipoMovimentoEstoque.CONSIGNACAO ||
               tipo == TipoMovimentoEstoque.ESTORNO_ENTRADA ||
               tipo == TipoMovimentoEstoque.ZERAR_ESTOQUE;
    }

    private void aplicarFefo(ProdutoSKU sku, BigDecimal quantidadeTotal, MovimentoEstoque movimento) {
        if (quantidadeTotal == null || quantidadeTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return; // Não há quantidade para consumir
        }

        BigDecimal restante = quantidadeTotal;
        List<EstoqueLote> lotes = estoqueLoteRepository.findByProdutoSkuIdOrderByDataValidadeAscNullsLast(sku.getId());

        for (EstoqueLote lote : lotes) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) {
                break; // Já consumimos toda a quantidade necessária
            }

            if (lote.getQuantidade().compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Pular lotes com quantidade zero ou negativa
            }

            BigDecimal consumir = restante.min(lote.getQuantidade());

            if (consumir.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Não há nada para consumir
            }

            // Atualizar a quantidade do lote
            BigDecimal quantidadeLote = lote.getQuantidade() == null ? BigDecimal.ZERO : lote.getQuantidade();
            BigDecimal novaQuantidade = quantidadeLote.subtract(consumir);
            lote.setQuantidade(novaQuantidade);
            lote.setUpdatedAt(LocalDateTime.now());
            estoqueLoteRepository.save(lote);

            // Criar registro de movimento de lote SOMENTE se consumir > 0
            MovimentoEstoqueLote movimentoLote = MovimentoEstoqueLote.builder()
                    .movimentoEstoque(movimento)
                    .estoqueLote(lote)
                    .quantidade(consumir)
                    .createdAt(LocalDateTime.now())
                    .build();

            movimentoEstoqueLoteRepository.save(movimentoLote);

            restante = restante.subtract(consumir);
        }

        // Se ainda houver restante após consumir todos os lotes, registrar em SEM_LOTE/SEM_VALIDADE
        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("SKU {} consumiu {} sem lastro de lote (quantidade total: {})",
                    sku.getSku(), restante, quantidadeTotal);

            String loteNormalizado = EstoqueLote.DEFAULT_LOTE;
            LocalDate dataValidadeNormalizada = EstoqueLote.DEFAULT_DATA_VALIDADE;

            EstoqueLote semLote = estoqueLoteRepository
                    .findBySkuLoteValidadeNullable(
                            sku.getId(),
                            loteNormalizado,
                            dataValidadeNormalizada,
                            EstoqueLote.DEFAULT_DATA_VALIDADE
                    )
                    .orElseGet(() -> EstoqueLote.builder()
                            .produtoSku(sku)
                            .lote(loteNormalizado)
                            .dataValidade(dataValidadeNormalizada)
                            .quantidade(BigDecimal.ZERO)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build());

            BigDecimal quantidadeSemLote = semLote.getQuantidade() == null ? BigDecimal.ZERO : semLote.getQuantidade();
            semLote.setQuantidade(quantidadeSemLote.subtract(restante)); // permite negativo para evidenciar falta de lastro
            semLote.setUpdatedAt(LocalDateTime.now());
            estoqueLoteRepository.save(semLote);

            MovimentoEstoqueLote movimentoSemLote = MovimentoEstoqueLote.builder()
                    .movimentoEstoque(movimento)
                    .estoqueLote(semLote)
                    .quantidade(restante)
                    .createdAt(LocalDateTime.now())
                    .build();
            movimentoEstoqueLoteRepository.save(movimentoSemLote);
        }
    }

    /**
     * Zera o estoque de todos os produtos que possuem quantidade > 0
     * Cria um movimento individual para cada SKU para manter auditoria completa
     */
    @Transactional
    public Map<String, Object> zerarTodoEstoque() {
        // Buscar usuário logado
        Usuario usuarioLogado = securityUtils.getUsuarioAtual();

        log.info("Usuário {} iniciando operação de zerar todo o estoque", usuarioLogado.getEmail());

        // Buscar todos os SKUs com estoque diferente de zero (inclui negativos)
        List<ProdutoSKU> skusComEstoque = skuRepository.findSkusComEstoqueDiferenteZero();

        if (skusComEstoque.isEmpty()) {
            log.info("Nenhum produto com estoque encontrado");
            return Map.of(
                    "produtosAfetados", 0,
                    "movimentosCriados", 0,
                    "detalhes", "Nenhum produto possuía estoque para ser zerado"
            );
        }

        List<MovimentoEstoqueDTO> movimentosCriados = new ArrayList<>();
        int produtosAfetados = 0;
        BigDecimal totalEstoqueZerado = BigDecimal.ZERO;

        // Processar cada SKU individualmente
        for (ProdutoSKU sku : skusComEstoque) {
            try {
                Estoque estoque = sku.getEstoque();

                // Se o estoque for nulo, criar um novo objeto Estoque
                if (estoque == null) {
                    estoque = criarEstoqueVazio();
                    estoque = estoqueRepository.save(estoque);
                    sku.setEstoque(estoque);
                    skuRepository.save(sku);
                }

                if (estoque.getQuantidade() != 0) { // Mudança: != 0 em vez de > 0

                    BigDecimal quantidadeAtual = BigDecimal.valueOf(estoque.getQuantidade());

                    // Criar movimento para zerar o estoque
                    MovimentoEstoque movimento = MovimentoEstoque.builder()
                            .sku(sku)
                            .produto(sku.getProduto())
                            .tipoMovimento(TipoMovimentoEstoque.ZERAR_ESTOQUE.getCodigo())
                            .quantidade(quantidadeAtual) // Registra quantidade que foi zerada
                            .estoqueAnterior(quantidadeAtual)
                            .estoqueAtual(BigDecimal.ZERO)
                            .observacao("Estoque zerado em operação em massa - Usuário: " + usuarioLogado.getEmail())
                            .usuario(usuarioLogado)
                            .build();

                    // Atualizar estoque para zero
                    estoque.setQuantidade(0);

                    // Salvar movimento e estoque
                    movimento = movimentoRepository.save(movimento);
                    estoqueRepository.save(estoque);

                    // Adicionar à lista de resultados
                    movimentosCriados.add(toDTO(movimento));
                    produtosAfetados++;
                    totalEstoqueZerado = totalEstoqueZerado.add(quantidadeAtual);

                    log.debug("Estoque do SKU {} zerado. Quantidade anterior: {}",
                            sku.getSku(), quantidadeAtual);

                    // Enviar notificação para o ERP
                    try {
                        erpNotificationService.notifyStockAdjustment(
                                sku.getSku(),
                                formatarDescricaoSku(sku),
                                quantidadeAtual.negate(), // Quantidade negativa indica remoção
                                quantidadeAtual,
                                BigDecimal.ZERO,
                                "Estoque zerado em operação em massa",
                                usuarioLogado.getId()
                        );
                    } catch (Exception e) {
                        log.warn("Erro ao enviar notificação ERP para SKU {}: {}",
                                sku.getSku(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Erro ao zerar estoque do SKU {}: ", sku.getSku(), e);
                // Continua com os próximos SKUs mesmo se um falhar
            }
        }

        log.info("Operação de zerar estoque concluída. Produtos afetados: {}, Total zerado: {}",
                produtosAfetados, totalEstoqueZerado);

        return Map.of(
                "produtosAfetados", produtosAfetados,
                "movimentosCriados", movimentosCriados.size(),
                "totalEstoqueZerado", totalEstoqueZerado,
                "detalhes", String.format("Operação realizada por %s", usuarioLogado.getEmail()),
                "movimentos", movimentosCriados
        );
    }

    /**
     * Zera o estoque de um SKU específico
     */
    @Transactional
    public MovimentoEstoqueDTO zerarEstoque(Long skuId, String motivo) {
        // Buscar usuário logado
        Usuario usuarioLogado = securityUtils.getUsuarioAtual();

        // Buscar SKU (com produto carregado)
        ProdutoSKU sku = skuRepository.findByIdWithProduto(skuId)
                .orElseThrow(() -> new RuntimeException(
                        messageResolver.getMessage("movimento.estoque.error.sku-not-found")));

        Estoque estoque = sku.getEstoque();
        if (estoque == null || estoque.getQuantidade() <= 0) {
            throw new RuntimeException("Produto já possui estoque zerado");
        }

        BigDecimal quantidadeAtual = BigDecimal.valueOf(estoque.getQuantidade());

        // Criar request para movimento
        MovimentoEstoqueRequest request = MovimentoEstoqueRequest.builder()
                .skuId(skuId)
                .tipoMovimento(TipoMovimentoEstoque.ZERAR_ESTOQUE.getCodigo())
                .quantidade(quantidadeAtual) // Para auditoria, registrar quantidade zerada
                .observacao(String.format("Estoque zerado - Motivo: %s - Usuário: %s",
                        motivo, usuarioLogado.getEmail()))
                .build();

        // Executar movimento usando método existente
        MovimentoEstoqueDTO movimento = movimentarEstoque(request);

        // Garantir que o usuário seja registrado no movimento
        MovimentoEstoque movimentoEntity = movimentoRepository.findById(movimento.getId())
                .orElseThrow(() -> new RuntimeException("Movimento não encontrado após criação"));
        movimentoEntity.setUsuario(usuarioLogado);
        movimentoRepository.save(movimentoEntity);

        log.info("Estoque do SKU {} zerado por usuário {}. Quantidade anterior: {}",
                sku.getSku(), usuarioLogado.getEmail(), quantidadeAtual);

        return movimento;
    }

    public List<MovimentoEstoqueAdminDTO> listarMovimentosPorItemPedidoId(Long itemPedidoId) {
        List<MovimentoEstoque> movimentos = movimentoRepository.findByItemPedidoIdOrderByDataMovimentoDesc(itemPedidoId);

        return movimentos.stream()
                .map(this::toAdminDTO)
                .collect(Collectors.toList());
    }

    public boolean existeEstornoParaMovimento(Long movimentoId) {
        return movimentoRepository.existsByMovimentoOrigemId(movimentoId);
    }

    public EstornarMovimentoEstoqueResponse estornarMovimento(Long movimentoId, EstornarMovimentoEstoqueRequest req) {
        // A) Validar existência do movimento original
        Optional<MovimentoEstoque> movimentoOriginalOpt = movimentoRepository.findById(movimentoId);
        if (movimentoOriginalOpt.isEmpty()) {
            return EstornarMovimentoEstoqueResponse.builder()
                    .sucesso(false)
                    .mensagem("Movimento original não encontrado.")
                    .movimentoOrigemId(movimentoId)
                    .jaExistia(false)
                    .build();
        }

        MovimentoEstoque movimentoOriginal = movimentoOriginalOpt.get();

        // B) Verificar idempotência - se já existe estorno para este movimento
        if (movimentoRepository.existsByMovimentoOrigemId(movimentoId)) {
            // Buscar o estorno existente
            List<MovimentoEstoque> estornos = movimentoRepository.findByMovimentoOrigemId(movimentoId);
            if (!estornos.isEmpty()) {
                MovimentoEstoque estornoExistente = estornos.get(0); // Pegar o primeiro
                return EstornarMovimentoEstoqueResponse.builder()
                        .sucesso(true)
                        .mensagem("Estorno já existente para este movimento.")
                        .estornoId(estornoExistente.getId())
                        .movimentoOrigemId(movimentoId)
                        .jaExistia(true)
                        .build();
            }
        }

        // C) Criar movimento de estorno
        Usuario usuarioLogado = null;
        try {
            usuarioLogado = securityUtils.getUsuarioAtual();
        } catch (Exception e) {
            // Se não conseguir obter usuário logado, continuar com null
        }

        // Determinar observação para o estorno
        String observacaoEstorno = "ESTORNO: ";
        if (req.getMotivo() != null && !req.getMotivo().trim().isEmpty()) {
            observacaoEstorno += req.getMotivo().trim() + ". ";
        }
        if (req.getObservacao() != null && !req.getObservacao().trim().isEmpty()) {
            observacaoEstorno += req.getObservacao().trim();
        }

        MovimentoEstoque movimentoEstorno = MovimentoEstoque.builder()
                .sku(movimentoOriginal.getSku())
                .produto(movimentoOriginal.getProduto())
                .tipoMovimento(movimentoOriginal.getTipoMovimento()) // Mesmo tipo do original
                .quantidade(movimentoOriginal.getQuantidade().negate()) // Quantidade negativa
                .estoqueAnterior(movimentoOriginal.getEstoqueAtual()) // Espelha os valores
                .estoqueAtual(movimentoOriginal.getEstoqueAnterior()) // Espelha os valores
                .quantidadeBase(movimentoOriginal.getQuantidadeBase() != null ?
                    -Math.abs(movimentoOriginal.getQuantidadeBase()) : null) // Negativo se existir
                .estoqueAnteriorBase(movimentoOriginal.getEstoqueAtualBase())
                .estoqueAtualBase(movimentoOriginal.getEstoqueAnteriorBase())
                .observacao(observacaoEstorno)
                .vendaId(movimentoOriginal.getVendaId())
                .recebimentoId(movimentoOriginal.getRecebimentoId())
                .consignacaoId(movimentoOriginal.getConsignacaoId())
                .documentoReferencia("ESTORNO DE MOVIMENTO #" + movimentoId)
                .usuario(usuarioLogado)
                .itemPedidoId(movimentoOriginal.getItemPedidoId()) // Copia do original
                .movimentoOrigemId(movimentoId) // Vincula ao movimento original
                .localOrigemId(movimentoOriginal.getLocalOrigemId())
                .localDestinoId(movimentoOriginal.getLocalDestinoId())
                .build();

        // Salvar o movimento de estorno
        MovimentoEstoque estornoSalvo = movimentoRepository.save(movimentoEstorno);

        return EstornarMovimentoEstoqueResponse.builder()
                .sucesso(true)
                .mensagem("Estorno criado com sucesso.")
                .estornoId(estornoSalvo.getId())
                .movimentoOrigemId(movimentoId)
                .jaExistia(false)
                .build();
    }

    @Transactional
    public List<Long> estornarMovimentosPorItemPedidoId(Long itemPedidoId, String motivo, String observacao) {
        // A) Buscar movimentos_estoque onde item_pedido_id = itemPedidoId E movimento_origem_id IS NULL
        List<MovimentoEstoque> movimentosOriginais = movimentoRepository.findByItemPedidoIdAndMovimentoOrigemIdIsNull(itemPedidoId);

        List<Long> estornosCriados = new ArrayList<>();

        for (MovimentoEstoque movimentoOriginal : movimentosOriginais) {
            // B) Verificar idempotência - se já existe estorno para este movimento
            if (movimentoRepository.existsByMovimentoOrigemId(movimentoOriginal.getId())) {
                continue; // Pular se já tem estorno
            }

            // C) Criar estorno igual ao estorno manual
            Usuario usuarioLogado = null;
            try {
                usuarioLogado = securityUtils.getUsuarioAtual();
            } catch (Exception e) {
                // Se não conseguir obter usuário logado, continuar com null
            }

            String motivoCompleto = (motivo != null ? motivo : "") + " " + (observacao != null ? observacao : "");
            String observacaoEstorno = "ESTORNO AUTO: cancelamento itemPedidoId=" + itemPedidoId + ". " + motivoCompleto.trim();

            MovimentoEstoque movimentoEstorno = MovimentoEstoque.builder()
                    .sku(movimentoOriginal.getSku())
                    .produto(movimentoOriginal.getProduto())
                    .tipoMovimento(movimentoOriginal.getTipoMovimento()) // Mesmo tipo do original
                    .quantidade(movimentoOriginal.getQuantidade().negate()) // Quantidade negativa
                    .estoqueAnterior(movimentoOriginal.getEstoqueAtual()) // Espelha os valores
                    .estoqueAtual(movimentoOriginal.getEstoqueAnterior()) // Espelha os valores
                    .quantidadeBase(movimentoOriginal.getQuantidadeBase() != null ?
                        -Math.abs(movimentoOriginal.getQuantidadeBase()) : null) // Negativo se existir
                    .estoqueAnteriorBase(movimentoOriginal.getEstoqueAtualBase())
                    .estoqueAtualBase(movimentoOriginal.getEstoqueAnteriorBase())
                    .observacao(observacaoEstorno)
                    .vendaId(movimentoOriginal.getVendaId())
                    .recebimentoId(movimentoOriginal.getRecebimentoId())
                    .consignacaoId(movimentoOriginal.getConsignacaoId())
                    .documentoReferencia("ESTORNO AUTO (CANCELAMENTO ITEM) DE MOVIMENTO #" + movimentoOriginal.getId())
                    .usuario(usuarioLogado)
                    .itemPedidoId(movimentoOriginal.getItemPedidoId()) // Copia do original
                    .movimentoOrigemId(movimentoOriginal.getId()) // Vincula ao movimento original
                    .localOrigemId(movimentoOriginal.getLocalOrigemId())
                    .localDestinoId(movimentoOriginal.getLocalDestinoId())
                    .build();

            // Salvar o movimento de estorno
            MovimentoEstoque estornoSalvo = movimentoRepository.save(movimentoEstorno);
            estornosCriados.add(estornoSalvo.getId());
        }

        return estornosCriados;
    }

    private MovimentoEstoqueAdminDTO toAdminDTO(MovimentoEstoque movimento) {
        return MovimentoEstoqueAdminDTO.builder()
                .id(movimento.getId())
                .itemPedidoId(movimento.getItemPedidoId())
                .tipoMovimento(movimento.getTipoMovimento())
                .quantidade(movimento.getQuantidade())
                .skuId(movimento.getSku() != null ? movimento.getSku().getId() : null)
                .produtoId(movimento.getProduto() != null ? movimento.getProduto().getId() : null)
                .documentoReferencia(movimento.getDocumentoReferencia())
                .observacao(movimento.getObservacao())
                .usuarioId(movimento.getUsuario() != null ? movimento.getUsuario().getId() : null)
                .dataMovimento(movimento.getDataMovimento())
                .build();
    }
}
