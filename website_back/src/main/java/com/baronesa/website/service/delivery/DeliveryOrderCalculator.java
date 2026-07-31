package com.baronesa.website.service.delivery;

import com.baronesa.website.dto.delivery.CreateDeliveryOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeliveryOrderCalculator {

    private final ErpCardapioClient cardapioClient;

    public CalculationResult calculate(List<CreateDeliveryOrderRequest.DeliveryItemRequest> items) {
        List<ErpCardapioClient.CardapioCategoria> cardapio = cardapioClient.fetchCardapioV2();
        List<CalculatedItem> calculated = new ArrayList<>();
        int totalCents = 0;

        for (CreateDeliveryOrderRequest.DeliveryItemRequest item : items) {
            CardapioMatch match = resolveItem(cardapio, item.produtoId(), item.skuId());
            String nome = match.nome();
            Integer priceCents = match.priceCents();
            Integer quantity = item.quantidade() == null ? 1 : item.quantidade();

            totalCents += priceCents * quantity;

            calculated.add(new CalculatedItem(
                    item.produtoId(),
                    item.skuId(),
                    nome,
                    quantity,
                    item.observacoes(),
                    item.size(),
                    priceCents,
                    match.variacao()
            ));
        }

        return new CalculationResult(calculated, totalCents);
    }

    private CardapioMatch resolveItem(List<ErpCardapioClient.CardapioCategoria> cardapio, Long produtoId, Long skuId) {
        if (cardapio == null) {
            return new CardapioMatch(fallbackName(produtoId, skuId), 0, null);
        }

        for (ErpCardapioClient.CardapioCategoria categoria : cardapio) {
            if (categoria.getProdutos() == null) continue;
            for (ErpCardapioClient.CardapioProduto produto : categoria.getProdutos()) {
                if (produto == null || produto.getId() == null) continue;
                if (!produto.getId().equals(produtoId)) continue;

                if (skuId != null && produto.getSkus() != null) {
                    Optional<ErpCardapioClient.CardapioSku> sku = produto.getSkus().stream()
                            .filter(s -> skuId.equals(s.getId()))
                            .findFirst();
                    if (sku.isPresent()) {
                        String nome = produto.getNome() + " (" + (sku.get().getVariacao() == null ? "SKU" : sku.get().getVariacao()) + ")";
                        int priceCents = toCents(resolvePrice(sku.get().getPrecoPromocional(), sku.get().getPreco()));
                        return new CardapioMatch(nome, priceCents, sku.get().getVariacao());
                    }
                }

                String nome = produto.getNome();
                int priceCents = toCents(resolvePrice(produto.getPrecoPromocional(), produto.getPreco_promocional(), produto.getPreco()));
                return new CardapioMatch(nome, priceCents, null);
            }
        }

        return new CardapioMatch(fallbackName(produtoId, skuId), 0, null);
    }

    private static String fallbackName(Long produtoId, Long skuId) {
        if (skuId != null) {
            return "Produto " + produtoId + " (SKU " + skuId + ")";
        }
        return "Produto " + produtoId;
    }

    private static BigDecimal resolvePrice(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v != null) return v;
        }
        return BigDecimal.ZERO;
    }

    private static int toCents(BigDecimal value) {
        if (value == null) return 0;
        return value.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    public record CalculationResult(List<CalculatedItem> items, int totalCents) {}

    public record CalculatedItem(
            Long produtoId,
            Long skuId,
            String nome,
            Integer quantidade,
            String observacoes,
            String size,
            Integer priceCents,
            String variacao
    ) {}

    private record CardapioMatch(String nome, int priceCents, String variacao) {}
}
