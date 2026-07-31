package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.enums.StatusPedidoCompra;
import com.baronesa.emporio.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoCompraService {

    private final PedidoCompraRepository pedidoCompraRepository;
    private final PedidoCompraItemRepository pedidoCompraItemRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository produtoSKURepository;
    private final EmbalagemRepository embalagemRepository;
    private final FornecedorRepository fornecedorRepository;

    @Transactional
    public PedidoCompraDTO atualizarPedido(Long id, AtualizarPedidoCompraRequest request) {
        PedidoCompra pedido = pedidoCompraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido de compra não encontrado"));

        // Validar transições de status
        if (request.getStatus() != null) {
            validarTransicaoStatus(pedido.getStatus(), request.getStatus());
            pedido.setStatus(request.getStatus());
        }

        if (request.getFornecedorId() != null) {
            Fornecedor fornecedor = fornecedorRepository.findById(request.getFornecedorId())
                    .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));
            pedido.setFornecedor(fornecedor);
        }

        if (request.getDataPrevista() != null) {
            pedido.setDataPrevista(request.getDataPrevista());
        }

        if (request.getObservacao() != null) {
            pedido.setObservacao(request.getObservacao());
        }

        pedido = pedidoCompraRepository.save(pedido);
        return toDTO(pedido);
    }

    @Transactional
    public PedidoCompraItemDTO adicionarItem(Long pedidoId, AdicionarItemPedidoRequest request) {
        PedidoCompra pedido = pedidoCompraRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido de compra não encontrado"));

        if (pedido.getStatus() != StatusPedidoCompra.RASCUNHO) {
            throw new RuntimeException("Apenas pedidos em rascunho podem ter itens adicionados");
        }

        Produto produto = produtoRepository.findById(request.getProdutoId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        ProdutoSKU sku = null;
        if (request.getSkuId() != null) {
            sku = produtoSKURepository.findById(request.getSkuId())
                    .orElseThrow(() -> new RuntimeException("SKU não encontrado"));
            if (!sku.getProduto().getId().equals(produto.getId())) {
                throw new RuntimeException("SKU não pertence ao produto");
            }
        }

        Embalagem embalagem = null;
        if (request.getEmbalagemId() != null) {
            embalagem = embalagemRepository.findById(request.getEmbalagemId())
                    .orElseThrow(() -> new RuntimeException("Embalagem não encontrada"));
            if (!embalagem.getProduto().getId().equals(produto.getId())) {
                throw new RuntimeException("Embalagem não pertence ao produto");
            }
        }

        if (request.getQuantidade() == null || request.getQuantidade().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Quantidade deve ser maior que zero");
        }

        Integer quantidadeBase = calcularQuantidadeBase(produto, embalagem, request.getQuantidade());
        BigDecimal subtotal = calcularSubtotal(request.getQuantidade(), request.getCustoUnitario());

        PedidoCompraItem item = PedidoCompraItem.builder()
                .pedido(pedido)
                .produto(produto)
                .sku(sku)
                .embalagem(embalagem)
                .quantidade(request.getQuantidade())
                .quantidadeBase(quantidadeBase)
                .custoUnitario(request.getCustoUnitario())
                .subtotal(subtotal)
                .build();

        item = pedidoCompraItemRepository.save(item);
        return toItemDTO(item);
    }

    @Transactional
    public PedidoCompraItemDTO atualizarItem(Long pedidoId, Long itemId, AtualizarItemPedidoRequest request) {
        PedidoCompra pedido = pedidoCompraRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido de compra não encontrado"));

        if (pedido.getStatus() != StatusPedidoCompra.RASCUNHO) {
            throw new RuntimeException("Apenas pedidos em rascunho podem ter itens editados");
        }

        PedidoCompraItem item = pedidoCompraItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item não encontrado"));

        if (!item.getPedido().getId().equals(pedidoId)) {
            throw new RuntimeException("Item não pertence ao pedido informado");
        }

        if (request.getQuantidade() != null) {
            if (request.getQuantidade().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Quantidade deve ser maior que zero");
            }
            item.setQuantidade(request.getQuantidade());
        }

        if (request.getCustoUnitario() != null) {
            item.setCustoUnitario(request.getCustoUnitario());
        }

        if (request.getEmbalagemId() != null && Boolean.TRUE.equals(item.getProduto().getInsumo())) {
            Embalagem embalagem = embalagemRepository.findById(request.getEmbalagemId())
                    .orElseThrow(() -> new RuntimeException("Embalagem não encontrada"));
            if (!embalagem.getProduto().getId().equals(item.getProduto().getId())) {
                throw new RuntimeException("Embalagem não pertence ao produto");
            }
            item.setEmbalagem(embalagem);
        }

        if (request.getSkuId() != null && Boolean.FALSE.equals(item.getProduto().getInsumo())) {
            ProdutoSKU sku = produtoSKURepository.findById(request.getSkuId())
                    .orElseThrow(() -> new RuntimeException("SKU não encontrado"));
            if (!sku.getProduto().getId().equals(item.getProduto().getId())) {
                throw new RuntimeException("SKU não pertence ao produto");
            }
            item.setSku(sku);
        }

        // Recalcular quantidade base e subtotal
        item.setQuantidadeBase(calcularQuantidadeBase(item.getProduto(), item.getEmbalagem(), item.getQuantidade()));
        item.setSubtotal(calcularSubtotal(item.getQuantidade(), item.getCustoUnitario()));

        item = pedidoCompraItemRepository.save(item);
        return toItemDTO(item);
    }

    @Transactional
    public void removerItem(Long pedidoId, Long itemId) {
        PedidoCompra pedido = pedidoCompraRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido de compra não encontrado"));

        if (pedido.getStatus() != StatusPedidoCompra.RASCUNHO) {
            throw new RuntimeException("Apenas pedidos em rascunho podem ter itens removidos");
        }

        PedidoCompraItem item = pedidoCompraItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item não encontrado"));

        if (!item.getPedido().getId().equals(pedidoId)) {
            throw new RuntimeException("Item não pertence ao pedido informado");
        }

        pedidoCompraItemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public Page<PedidoCompraDTO> listarPedidos(
            StatusPedidoCompra status,
            Long fornecedorId,
            LocalDate de,
            LocalDate ate,
            String search,
            int page,
            int size
    ) {
        Specification<PedidoCompra> spec = Specification.where(null);

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (fornecedorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("fornecedor").get("id"), fornecedorId));
        }

        if (de != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("criadoEm"), de.atStartOfDay()));
        }

        if (ate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("criadoEm"), ate.plusDays(1).atStartOfDay()));
        }

        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                cb.or(
                    cb.like(cb.lower(root.get("observacao")), "%" + search.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("fornecedor").get("nome")), "%" + search.toLowerCase() + "%")
                )
            );
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "criadoEm"));
        Page<PedidoCompra> pedidos = pedidoCompraRepository.findAll(spec, pageable);

        return pedidos.map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public PedidoCompraDTO buscarPorId(Long id) {
        PedidoCompra pedido = pedidoCompraRepository.findByIdWithItens(id);
        if (pedido == null) {
            throw new RuntimeException("Pedido de compra não encontrado");
        }
        return toDTO(pedido);
    }

    private void validarTransicaoStatus(StatusPedidoCompra atual, StatusPedidoCompra novo) {
        if (atual == StatusPedidoCompra.CANCELADO) {
            throw new RuntimeException("Pedido cancelado não pode ter status alterado");
        }

        if (atual == StatusPedidoCompra.RECEBIDO) {
            throw new RuntimeException("Pedido já recebido não pode ter status alterado");
        }

        if (atual == StatusPedidoCompra.RASCUNHO && novo == StatusPedidoCompra.PARCIAL) {
            throw new RuntimeException("Pedido em rascunho não pode ser marcado como parcial");
        }

        if (atual == StatusPedidoCompra.RASCUNHO && novo == StatusPedidoCompra.RECEBIDO) {
            throw new RuntimeException("Pedido em rascunho não pode ser marcado como recebido");
        }
    }

    private Integer calcularQuantidadeBase(Produto produto, Embalagem embalagem, BigDecimal quantidade) {
        if (Boolean.TRUE.equals(produto.getInsumo())) {
            int fator = 1;
            if (embalagem != null && embalagem.getFatorBase() != null && embalagem.getFatorBase() > 0) {
                fator = embalagem.getFatorBase();
            }
            return quantidade != null ? quantidade.intValue() * fator : null;
        }
        return null;
    }

    private BigDecimal calcularSubtotal(BigDecimal quantidade, BigDecimal custoUnitario) {
        if (quantidade != null && custoUnitario != null) {
            return quantidade.multiply(custoUnitario).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        return null;
    }

    private PedidoCompraDTO toDTO(PedidoCompra pedido) {
        List<PedidoCompraItemDTO> itensDTO = pedido.getItens() != null
                ? pedido.getItens().stream().map(this::toItemDTO).collect(Collectors.toList())
                : List.of();

        BigDecimal valorTotal = itensDTO.stream()
                .map(PedidoCompraItemDTO::getSubtotal)
                .filter(s -> s != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PedidoCompraDTO.builder()
                .id(pedido.getId())
                .fornecedorId(pedido.getFornecedor() != null ? pedido.getFornecedor().getId() : null)
                .fornecedorNome(pedido.getFornecedor() != null ? pedido.getFornecedor().getNomeExibicao() : null)
                .status(pedido.getStatus())
                .dataPrevista(pedido.getDataPrevista())
                .observacao(pedido.getObservacao())
                .criadoEm(pedido.getCriadoEm())
                .atualizadoEm(pedido.getAtualizadoEm())
                .itens(itensDTO)
                .valorTotal(valorTotal)
                .build();
    }

    private PedidoCompraItemDTO toItemDTO(PedidoCompraItem item) {
        return PedidoCompraItemDTO.builder()
                .id(item.getId())
                .produtoId(item.getProduto() != null ? item.getProduto().getId() : null)
                .produtoNome(item.getProduto() != null ? item.getProduto().getNome() : null)
                .skuId(item.getSku() != null ? item.getSku().getId() : null)
                .skuNome(item.getSku() != null ? item.getSku().getSku() : null)
                .embalagemId(item.getEmbalagem() != null ? item.getEmbalagem().getId() : null)
                .embalagemNome(item.getEmbalagem() != null ? item.getEmbalagem().getNome() : null)
                .quantidade(item.getQuantidade())
                .quantidadeBase(item.getQuantidadeBase())
                .custoUnitario(item.getCustoUnitario())
                .subtotal(item.getSubtotal())
                .recebidoBase(item.getRecebidoBase())
                .status(item.getStatus())
                .build();
    }
}
