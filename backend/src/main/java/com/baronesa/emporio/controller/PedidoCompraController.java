package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.enums.StatusPedidoCompra;
import com.baronesa.emporio.repository.PedidoCompraRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import com.baronesa.emporio.repository.ProdutoSKURepository;
import com.baronesa.emporio.repository.EmbalagemRepository;
import com.baronesa.emporio.service.PedidoCompraService;
import com.baronesa.emporio.service.SugestaoCompraService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/pedidos-compra")
@RequiredArgsConstructor
public class PedidoCompraController {

    private final SugestaoCompraService sugestaoCompraService;
    private final PedidoCompraService pedidoCompraService;
    private final PedidoCompraRepository pedidoCompraRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository produtoSKURepository;
    private final EmbalagemRepository embalagemRepository;

    @GetMapping("/sugestoes")
    public ResponseEntity<List<SugestaoCompraItemDTO>> listarSugestoes(@RequestParam(name = "somenteCriticos", defaultValue = "true") boolean somenteCriticos) {
        return ResponseEntity.ok(sugestaoCompraService.gerarSugestoes(somenteCriticos));
    }

    // DTOs mínimos para criação de pedido sem fornecedor
    public record CriarPedidoRequest(
            List<Item> itens,
            String observacao
    ) {
        public record Item(Long produtoId, Long skuId, Long embalagemId, BigDecimal quantidade, BigDecimal custoUnitario) {}
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> criarPedido(@RequestBody CriarPedidoRequest request) {
        if (request == null || request.itens() == null || request.itens().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Itens são obrigatórios"));
        }

        PedidoCompra pedido = PedidoCompra.builder()
                .status(StatusPedidoCompra.RASCUNHO)
                .observacao(request.observacao())
                .build();

        for (CriarPedidoRequest.Item it : request.itens()) {
            Produto produto = produtoRepository.findById(it.produtoId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

            ProdutoSKU sku = null;
            if (it.skuId() != null) {
                sku = produtoSKURepository.findById(it.skuId())
                        .orElseThrow(() -> new RuntimeException("SKU não encontrado"));
                if (!sku.getProduto().getId().equals(produto.getId())) {
                    throw new RuntimeException("SKU não pertence ao produto");
                }
            }

            Embalagem embalagem = null;
            if (it.embalagemId() != null) {
                embalagem = embalagemRepository.findById(it.embalagemId())
                        .orElseThrow(() -> new RuntimeException("Embalagem não encontrada"));
                if (!embalagem.getProduto().getId().equals(produto.getId())) {
                    throw new RuntimeException("Embalagem não pertence ao produto");
                }
            }

            Integer quantidadeBase = null;
            if (Boolean.TRUE.equals(produto.getInsumo())) {
                int fator = 1;
                if (embalagem != null && embalagem.getFatorBase() != null && embalagem.getFatorBase() > 0) {
                    fator = embalagem.getFatorBase();
                }
                quantidadeBase = it.quantidade() != null ? it.quantidade().intValue() * fator : null;
            }

            PedidoCompraItem item = PedidoCompraItem.builder()
                    .pedido(pedido)
                    .produto(produto)
                    .sku(sku)
                    .embalagem(embalagem)
                    .quantidade(it.quantidade())
                    .quantidadeBase(quantidadeBase)
                    .custoUnitario(it.custoUnitario())
                    .subtotal(it.custoUnitario() != null && it.quantidade() != null ? it.custoUnitario().multiply(it.quantidade()) : null)
                    .build();

            pedido.addItem(item);
        }

        pedido = pedidoCompraRepository.save(pedido);

        return ResponseEntity.ok(Map.of(
                "id", pedido.getId(),
                "status", pedido.getStatus().name(),
                "itens", pedido.getItens().size()
        ));
    }

    @GetMapping
    public ResponseEntity<Page<PedidoCompraDTO>> listarPedidos(
            @RequestParam(required = false) StatusPedidoCompra status,
            @RequestParam(required = false) Long fornecedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(pedidoCompraService.listarPedidos(status, fornecedorId, de, ate, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoCompraDTO> buscarPedido(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoCompraService.buscarPorId(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PedidoCompraDTO> atualizarPedido(
            @PathVariable Long id,
            @RequestBody AtualizarPedidoCompraRequest request
    ) {
        return ResponseEntity.ok(pedidoCompraService.atualizarPedido(id, request));
    }

    @PostMapping("/{id}/itens")
    public ResponseEntity<PedidoCompraItemDTO> adicionarItem(
            @PathVariable Long id,
            @RequestBody AdicionarItemPedidoRequest request
    ) {
        return ResponseEntity.ok(pedidoCompraService.adicionarItem(id, request));
    }

    @PatchMapping("/{id}/itens/{itemId}")
    public ResponseEntity<PedidoCompraItemDTO> atualizarItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestBody AtualizarItemPedidoRequest request
    ) {
        return ResponseEntity.ok(pedidoCompraService.atualizarItem(id, itemId, request));
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    public ResponseEntity<Void> removerItem(
            @PathVariable Long id,
            @PathVariable Long itemId
    ) {
        pedidoCompraService.removerItem(id, itemId);
        return ResponseEntity.noContent().build();
    }
}
