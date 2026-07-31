package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.enums.TipoMovimentoEstoque;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class RecebimentoMercadoriaService {

    private final RecebimentoMercadoriaRepository recebimentoRepository;
    private final RecebimentoItemRepository itemRepository;
    private final FornecedorRepository fornecedorRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository produtoSKURepository;
    private final EmbalagemRepository embalagemRepository;
    private final MovimentoEstoqueService movimentoEstoqueService;
    private final UsuarioService usuarioService;
    private final EstoqueLoteRepository estoqueLoteRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final MovimentoEstoqueLoteRepository movimentoEstoqueLoteRepository;

    // Buscar por ID
    @Transactional(readOnly = true)
    public RecebimentoDTO buscarPorId(Long id) {
        if (id == null || id == 0) {
            // Retornar novo recebimento vazio
            return new RecebimentoDTO(
                    null,
                    "",
                    null,
                    null,
                    "",
                    "",
                    LocalDateTime.now(),
                    null,
                    BigDecimal.ZERO,
                    0,
                    StatusRecebimento.PENDENTE.name(),
                    StatusRecebimento.PENDENTE.getLabel(),
                    StatusRecebimento.PENDENTE.getColor(),
                    "",
                    new ArrayList<>(), // Lista vazia ao invés de null
                    true,
                    false,
                    false
            );
        }

        RecebimentoMercadoria recebimento = recebimentoRepository
                .findByIdWithAllRelations(id)
                .orElseThrow(() -> new RuntimeException("Recebimento não encontrado"));

        return toDTO(recebimento);
    }

    // Criar novo recebimento
    @Transactional
    public RecebimentoDTO criar(RecebimentoRequest request) {
        // Validar se NF já existe
        if (recebimentoRepository.existsByNumeroNfAndFornecedorId(request.numeroNf(), request.fornecedorId())) {
            throw new RuntimeException("Nota Fiscal já cadastrada para este fornecedor");
        }

        // Buscar fornecedor
        Fornecedor fornecedor = fornecedorRepository.findById(request.fornecedorId())
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));

        // Criar recebimento
        RecebimentoMercadoria recebimento = RecebimentoMercadoria.builder()
                .numeroNf(request.numeroNf())
                .fornecedor(fornecedor)
                .dataRecebimento(request.dataRecebimento() != null ? request.dataRecebimento() : LocalDateTime.now())
                .dataEmissaoNf(request.dataEmissaoNf())
                .observacao(request.observacao())
                .status(StatusRecebimento.PENDENTE)
                .build();

        recebimento = recebimentoRepository.save(recebimento);

        // Adicionar itens se houver
        if (request.itens() != null && !request.itens().isEmpty()) {
            for (RecebimentoItemRequest itemRequest : request.itens()) {
                adicionarItem(recebimento, itemRequest);
            }
            recebimento.recalcularTotais();
            recebimento = recebimentoRepository.save(recebimento);
        }

        return toDTO(recebimento);
    }

    // Editar recebimento
    @Transactional
    public RecebimentoDTO editar(Long id, RecebimentoRequest request) {
        RecebimentoMercadoria recebimento = recebimentoRepository
                .findByIdWithAllRelations(id)
                .orElseThrow(() -> new RuntimeException("Recebimento não encontrado"));

        if (!recebimento.podeEditar()) {
            throw new RuntimeException("Recebimento não pode ser editado");
        }

        // Atualizar dados básicos
        recebimento.setNumeroNf(request.numeroNf());
        recebimento.setDataRecebimento(request.dataRecebimento());
        recebimento.setDataEmissaoNf(request.dataEmissaoNf());
        recebimento.setObservacao(request.observacao());

        // Atualizar fornecedor se mudou
        if (!recebimento.getFornecedor().getId().equals(request.fornecedorId())) {
            Fornecedor fornecedor = fornecedorRepository.findById(request.fornecedorId())
                    .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));
            recebimento.setFornecedor(fornecedor);
        }

        // Limpar itens antigos
        recebimento.getItens().clear();

        // Adicionar novos itens
        if (request.itens() != null) {
            for (RecebimentoItemRequest itemRequest : request.itens()) {
                adicionarItem(recebimento, itemRequest);
            }
        }

        recebimento.recalcularTotais();
        recebimento = recebimentoRepository.save(recebimento);

        return toDTO(recebimento);
    }

    // Deletar recebimento
    @Transactional
    public void deletar(Long id) {
        RecebimentoMercadoria recebimento = recebimentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recebimento não encontrado"));

        if (!recebimento.podeEditar()) {
            throw new RuntimeException("Recebimento não pode ser excluído");
        }

        recebimentoRepository.delete(recebimento);
    }

    // Finalizar recebimento
    @Transactional
    public RecebimentoDTO finalizar(Long id) {
        RecebimentoMercadoria recebimento = recebimentoRepository
                .findByIdWithAllRelations(id)
                .orElseThrow(() -> new RuntimeException("Recebimento não encontrado"));

        if (!recebimento.podeFinalizar()) {
            throw new RuntimeException("Recebimento não pode ser finalizado. Verifique se há itens e quantidades válidas.");
        }

        // Validar controle de validade para cada item
        for (RecebimentoItem item : recebimento.getItens()) {
            // Obter o produto a partir do SKU do item para verificar o controle de validade
            Produto produto = null;

            // Primeiro tenta obter do SKU do item, se existir
            if (item.getSku() != null) {
                produto = item.getSku().getProduto();
            } else {
                // Fallback para o produto direto do item
                produto = item.getProduto();
            }

            if (produto != null && Boolean.TRUE.equals(produto.getControlaValidade())) {
                // Verificar se lote está preenchido
                if (item.getLote() == null || item.getLote().trim().isEmpty()) {
                    throw new BusinessException("Produto '" + produto.getNome() + "' exige controle de validade, mas lote não foi informado");
                }

                // Verificar se data de validade está preenchida
                if (item.getDataValidade() == null) {
                    throw new BusinessException("Produto '" + produto.getNome() + "' exige controle de validade, mas data de validade não foi informada");
                }
            }
        }

        // Persistir entradas no estoque_lote para produtos que controlam validade
        for (RecebimentoItem item : recebimento.getItens()) {
            // Obter o produto a partir do SKU do item para verificar o controle de validade
            Produto produto = null;

            // Primeiro tenta obter do SKU do item, se existir
            if (item.getSku() != null) {
                produto = item.getSku().getProduto();
            } else {
                // Fallback para o produto direto do item
                produto = item.getProduto();
            }

            if (produto != null && Boolean.TRUE.equals(produto.getControlaValidade())) {
                // Normalizar chave lógica (null -> sentinelas)
                String loteNormalizado = item.getLote() == null ? "" : item.getLote();
                LocalDate dataValidadeNormalizada = item.getDataValidade() != null
                        ? item.getDataValidade()
                        : EstoqueLote.DEFAULT_DATA_VALIDADE;

                // Buscar ou criar registro no estoque_lote
                Optional<EstoqueLote> estoqueLoteOpt = estoqueLoteRepository.findBySkuLoteValidadeNullable(
                        item.getSku().getId(),
                        loteNormalizado,
                        dataValidadeNormalizada,
                        EstoqueLote.DEFAULT_DATA_VALIDADE
                );

                EstoqueLote estoqueLote;
                if (estoqueLoteOpt.isPresent()) {
                    // Atualizar quantidade existente
                    estoqueLote = estoqueLoteOpt.get();
                    estoqueLote.setQuantidade(estoqueLote.getQuantidade().add(item.getQuantidade()));
                } else {
                    // Criar novo registro
                    estoqueLote = EstoqueLote.builder()
                        .produtoSku(item.getSku())
                        .lote(item.getLote())
                        .dataValidade(item.getDataValidade())
                        .quantidade(item.getQuantidade())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                }

                // Atualizar updatedAt
                estoqueLote.setUpdatedAt(LocalDateTime.now());

                // Salvar no banco
                estoqueLoteRepository.save(estoqueLote);
            }
        }

        // Gerar movimentos de estoque para cada SKU do produto
        for (RecebimentoItem item : recebimento.getItens()) {
            ProdutoSKU skuParaMovimento = null;
            Produto produtoDoItem = null;
            if (item.getSku() != null) {
                skuParaMovimento = item.getSku();
                produtoDoItem = item.getSku().getProduto();
            } else {
                // Fallback para lógica anterior
                List<ProdutoSKU> skus = produtoRepository.findById(item.getProduto().getId())
                        .map(p -> p.getSkus())
                        .orElse(new ArrayList<>());

                if (skus.isEmpty()) {
                    throw new RuntimeException("Produto " + item.getProduto().getNome() + " não possui SKUs cadastrados");
                }

                if (skus.size() == 1) {
                    skuParaMovimento = skus.get(0);
                } else {
                    skuParaMovimento = skus.stream()
                            .filter(s -> "Único".equalsIgnoreCase(s.getVariacao()))
                            .findFirst()
                            .orElse(skus.get(0));
                }
                produtoDoItem = item.getProduto();
            }

            MovimentoEstoqueRequest movimentoRequest = MovimentoEstoqueRequest.builder()
                    .skuId(skuParaMovimento.getId())
                    .tipoMovimento(TipoMovimentoEstoque.ENTRADA.getCodigo())
                    .quantidade(item.getQuantidade())
                    .observacao("Recebimento de Mercadoria #" + recebimento.getId())
                    .documentoReferencia("NF " + recebimento.getNumeroNf())
                    .recebimentoId(recebimento.getId())
                    .build();

            var movimentoDTO = movimentoEstoqueService.movimentarEstoque(movimentoRequest);

            // Registrar auditoria por lote no sub-ledger quando produto controla validade
            if (produtoDoItem != null && Boolean.TRUE.equals(produtoDoItem.getControlaValidade())) {
                String loteNormalizado = item.getLote() == null ? "" : item.getLote();
                LocalDate dataValidadeNormalizada = item.getDataValidade() != null
                        ? item.getDataValidade()
                        : EstoqueLote.DEFAULT_DATA_VALIDADE;

                EstoqueLote estoqueLote = estoqueLoteRepository.findBySkuLoteValidadeNullable(
                                skuParaMovimento.getId(),
                                loteNormalizado,
                                dataValidadeNormalizada,
                                EstoqueLote.DEFAULT_DATA_VALIDADE
                        )
                        .orElseThrow(() -> new RuntimeException("Lote não encontrado para registrar auditoria de entrada"));

                MovimentoEstoque movimento = movimentoEstoqueRepository.findById(movimentoDTO.getId())
                        .orElseThrow(() -> new RuntimeException("Movimento de estoque não encontrado para auditoria de lote"));

                MovimentoEstoqueLote movimentoLote = MovimentoEstoqueLote.builder()
                        .movimentoEstoque(movimento)
                        .estoqueLote(estoqueLote)
                        .quantidade(item.getQuantidade())
                        .createdAt(LocalDateTime.now())
                        .build();

                movimentoEstoqueLoteRepository.save(movimentoLote);
            }
        }

        // Atualizar status
        recebimento.finalizar();
        recebimento = recebimentoRepository.save(recebimento);

        log.info("Recebimento {} finalizado com sucesso", recebimento.getId());

        return toDTO(recebimento);
    }


    // Cancelar recebimento
    @Transactional
    public RecebimentoDTO cancelar(Long id) {
        RecebimentoMercadoria recebimento = recebimentoRepository
                .findByIdWithAllRelations(id)
                .orElseThrow(() -> new RuntimeException("Recebimento não encontrado"));

        if (!recebimento.podeCancelar()) {
            throw new RuntimeException("Recebimento não pode ser cancelado");
        }

        // Se o recebimento estava finalizado, fazer estorno do estoque
        if (recebimento.getStatus() == StatusRecebimento.FINALIZADO) {
            log.info("Iniciando estorno de estoque do recebimento {}", recebimento.getId());

            // Estornar movimentos de estoque para cada item
            for (RecebimentoItem item : recebimento.getItens()) {
                ProdutoSKU skuParaMovimento = null;
                if (item.getSku() != null) {
                    skuParaMovimento = item.getSku();
                } else {
                    List<ProdutoSKU> skus = produtoRepository.findById(item.getProduto().getId())
                            .map(p -> p.getSkus())
                            .orElse(new ArrayList<>());
                    if (skus.isEmpty()) {
                        log.warn("Produto {} não possui SKUs para estornar", item.getProduto().getNome());
                        continue;
                    }
                    if (skus.size() == 1) {
                        skuParaMovimento = skus.get(0);
                    } else {
                        skuParaMovimento = skus.stream()
                                .filter(s -> "Único".equalsIgnoreCase(s.getVariacao()))
                                .findFirst()
                                .orElse(skus.get(0));
                    }
                }

                MovimentoEstoqueRequest movimentoRequest = MovimentoEstoqueRequest.builder()
                        .skuId(skuParaMovimento.getId())
                        .tipoMovimento(TipoMovimentoEstoque.ESTORNO_ENTRADA.getCodigo())
                        .quantidade(item.getQuantidade())
                        .observacao("Cancelamento de Recebimento #" + recebimento.getId())
                        .documentoReferencia("ESTORNO NF " + recebimento.getNumeroNf())
                        .recebimentoId(recebimento.getId())
                        .build();

                movimentoEstoqueService.movimentarEstoque(movimentoRequest);
            }

            log.info("Estorno de estoque concluído para recebimento {}", recebimento.getId());
        }

        recebimento.cancelar();
        recebimento = recebimentoRepository.save(recebimento);

        log.info("Recebimento {} cancelado com sucesso", recebimento.getId());

        return toDTO(recebimento);
    }

    private void adicionarItem(RecebimentoMercadoria recebimento, RecebimentoItemRequest itemRequest) {
        Produto produto = produtoRepository.findById(itemRequest.produtoId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        // Garantir que custoUnitario não seja nulo
        BigDecimal custoUnitario = itemRequest.custoUnitario();
        if (custoUnitario == null) {
            custoUnitario = BigDecimal.ZERO;
        }

        RecebimentoItem item = RecebimentoItem.builder()
                .produto(produto)
                .quantidade(itemRequest.quantidade())
                .custoUnitario(custoUnitario)
                .lote(itemRequest.lote())
                .dataValidade(itemRequest.dataValidade())
                .build();

        // Preferência 1: se veio skuId, usar diretamente (cenário não-insumo)
        if (itemRequest.skuId() != null) {
            ProdutoSKU sku = produtoSKURepository.findById(itemRequest.skuId())
                    .orElseThrow(() -> new RuntimeException("SKU não encontrado"));
            if (!sku.getProduto().getId().equals(produto.getId())) {
                throw new RuntimeException("SKU informado não pertence ao produto selecionado");
            }
            item.setSku(sku);

        // Preferência 2: se veio embalagemId, tentar vincular um SKU do produto com essa embalagem
        } else if (itemRequest.embalagemId() != null) {
            Long embalagemId = itemRequest.embalagemId();
            // Buscar SKU existente do produto com a embalagem
            List<ProdutoSKU> skus = produtoSKURepository.findByProdutoIdAndEmbalagemId(produto.getId(), embalagemId);
            ProdutoSKU sku = null;
            if (skus != null && !skus.isEmpty()) {
                sku = skus.get(0);
            } else {
                // Criar SKU básico vinculado à embalagem quando inexistente
                Embalagem embalagem = embalagemRepository.findById(embalagemId)
                        .orElseThrow(() -> new RuntimeException("Embalagem não encontrada"));
                if (!embalagem.getProduto().getId().equals(produto.getId())) {
                    throw new RuntimeException("Embalagem não pertence ao produto selecionado");
                }

                sku = ProdutoSKU.builder()
                        .produto(produto)
                        .variacao(embalagem.getNome())
                        .codigoBarras(null)
                        .precoCusto(null)
                        .precoVenda(null)
                        .embalagem(embalagem)
                        .ativo(true)
                        .principal(false)
                        .build();
                sku.gerarSKU();
                sku = produtoSKURepository.save(sku);
            }
            item.setSku(sku);
        } else {
            // Sem skuId e sem embalagem: comportamento legado
            // Tenta escolher um SKU do produto: se houver 1, usa-o; senão, o primeiro ativo
            List<ProdutoSKU> skus = produto.getSkus();
            if (skus != null && !skus.isEmpty()) {
                ProdutoSKU sku = skus.size() == 1 ? skus.get(0) : skus.stream()
                        .filter(s -> Boolean.TRUE.equals(s.getAtivo()))
                        .findFirst().orElse(skus.get(0));
                item.setSku(sku);
            }
        }

        item.calcularValorTotal();
        recebimento.addItem(item);
    }


    // Conversão para DTO
    private RecebimentoDTO toDTO(RecebimentoMercadoria recebimento) {
        List<RecebimentoItemDTO> itensDTO = recebimento.getItens() != null
                ? recebimento.getItens().stream()
                .map(this::toItemDTO)
                .collect(Collectors.toList())
                : new ArrayList<>(); // Lista vazia se for null

        return new RecebimentoDTO(
                recebimento.getId(),
                recebimento.getNumeroNf(),
                recebimento.getChaveNfe(),
                recebimento.getFornecedor() != null ? recebimento.getFornecedor().getId() : null,
                recebimento.getFornecedor() != null ? recebimento.getFornecedor().getRazaoSocial() : "",
                recebimento.getFornecedor() != null ? recebimento.getFornecedor().getCnpj() : "",
                recebimento.getDataRecebimento(),
                recebimento.getDataEmissaoNf(),
                recebimento.getValorTotal() != null ? recebimento.getValorTotal() : BigDecimal.ZERO,
                recebimento.getQuantidadeItens() != null ? recebimento.getQuantidadeItens() : 0,
                recebimento.getStatus().name(),
                recebimento.getStatus().getLabel(),
                recebimento.getStatus().getColor(),
                recebimento.getObservacao(),
                itensDTO, // Sempre uma lista, nunca null
                recebimento.podeEditar(),
                recebimento.podeFinalizar(),
                recebimento.podeCancelar()
        );
    }

    private RecebimentoItemDTO toItemDTO(RecebimentoItem item) {
        Produto produto = item.getProduto();
        Long skuId = item.getSku() != null ? item.getSku().getId() : null;
        Long embalagemId = (item.getSku() != null && item.getSku().getEmbalagem() != null)
                ? item.getSku().getEmbalagem().getId() : null;

        return new RecebimentoItemDTO(
                item.getId(),
                produto.getId(),
                skuId,
                embalagemId,
                produto.getCodigoInterno(),
                produto.getNome(),
                item.getQuantidade(),
                item.getCustoUnitario(),
                item.getValorTotal(),
                item.getLote(),
                item.getDataValidade(),
                item.getCodigoProdutoFornecedor(),
                item.getDescricaoNfe()
        );
    }

    // Buscar opções para dropdown
    public List<Map<String, Object>> buscarOptions() {
        return recebimentoRepository.findAll().stream()
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("value", r.getId());
                    map.put("label", r.getNumeroNf() + " - " + r.getFornecedor().getRazaoSocial());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
