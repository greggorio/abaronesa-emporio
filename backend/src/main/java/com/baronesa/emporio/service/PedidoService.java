package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.MovimentoEstoqueRequest;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.enums.TipoMovimentoEstoque;
import com.baronesa.emporio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoService {

    private final ItemPedidoRepository itemPedidoRepository;
    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final MovimentoEstoqueService movimentoEstoqueService;
    private final GamificacaoService gamificacaoService;

    /**
     * Atualiza o status de um item do pedido.
     * Se o novo status for ACCEPTED e o produto tiver ficha técnica,
     * faz a baixa automática dos insumos.
     * Se o novo status for CANCELED, faz o estorno automático dos movimentos de estoque.
     */
    @Transactional
    public ItemPedido atualizarStatusItem(Long itemPedidoId, StatusItem novoStatus, String motivoCodigo, String motivoDetalhe) {
        ItemPedido item = itemPedidoRepository.findById(itemPedidoId)
                .orElseThrow(() -> new RuntimeException("Item de pedido não encontrado"));

        StatusItem statusAnterior = item.getStatus();
        item.setStatus(novoStatus);
        if (novoStatus == StatusItem.CANCELED) {
            item.setMotivoCancelamentoCodigo(parseMotivo(motivoCodigo));
            item.setMotivoCancelamento(trimOrNull(motivoDetalhe));
        }
        item = itemPedidoRepository.save(item);

        // Se mudou para ACCEPTED, processar baixa de insumos
        if (novoStatus == StatusItem.ACCEPTED && statusAnterior != StatusItem.ACCEPTED) {
            // 1) Se houver ficha técnica, baixa insumos conforme ficha
            processarBaixaInsumos(item);
            // 2) Baixa de estoque do SKU (para produtos vendáveis que controlam estoque)
            processarBaixaSku(item);
            Usuario cliente = null;
            if (item.getPedido() != null) {
                SessaoConvidado convidado = item.getPedido().getSessaoConvidado();
                if (convidado != null) {
                    cliente = convidado.getUsuario();
                }
            }
            if (cliente != null) {
                gamificacaoService.registrarConsumoItemAceito(item, cliente);
            }
        }

        // Se mudou para CANCELED, processar estorno de movimentos de estoque
        if (novoStatus == StatusItem.CANCELED) {
            // Estornar automaticamente se houver movimentos de estoque vinculados ao item
            movimentoEstoqueService.estornarMovimentosPorItemPedidoId(item.getId(), motivoCodigo, motivoDetalhe);
        }

        return item;
    }

    private String trimOrNull(String val) {
        if (val == null) return null;
        String t = val.trim();
        return t.isEmpty() ? null : t;
    }

    private com.baronesa.emporio.enums.MotivoCancelamentoItem parseMotivo(String codigo) {
        if (codigo == null || codigo.isBlank()) return com.baronesa.emporio.enums.MotivoCancelamentoItem.OUTRO;
        try {
            return com.baronesa.emporio.enums.MotivoCancelamentoItem.valueOf(codigo.toUpperCase());
        } catch (Exception e) {
            return com.baronesa.emporio.enums.MotivoCancelamentoItem.OUTRO;
        }
    }

    /**
     * Processa a baixa dos insumos quando um produto com ficha técnica é aceito.
     */
    @Transactional
    public void processarBaixaInsumos(ItemPedido item) {
        Produto produto = item.getProduto();

        // Verificar se produto tem ficha técnica
        if (produto == null || !Boolean.TRUE.equals(produto.getTemFichaTecnica())) {
            log.debug("Produto {} não possui ficha técnica. Pulando baixa de insumos.",
                    produto != null ? produto.getNome() : "null");
            return;
        }

        if (Boolean.TRUE.equals(produto.getProducaoPropria())) {
            log.debug("Produto {} com produção própria. Baixa de insumos ocorre na produção.",
                    produto.getNome());
            return;
        }

        // Buscar ficha técnica com itens
        Optional<FichaTecnica> fichaOpt = fichaTecnicaRepository.findByProdutoIdWithItens(produto.getId());

        if (fichaOpt.isEmpty()) {
            log.warn("Produto {} marcado com ficha técnica mas nenhuma ficha encontrada!", produto.getNome());
            return;
        }

        FichaTecnica ficha = fichaOpt.get();

        if (ficha.getItens() == null || ficha.getItens().isEmpty()) {
            log.warn("Ficha técnica do produto {} não possui ingredientes!", produto.getNome());
            return;
        }

        log.info("Processando baixa de insumos para produto {} (Pedido #{}, Item #{})",
                produto.getNome(), item.getPedido().getId(), item.getId());

        // Para cada ingrediente da ficha técnica
        for (FichaTecnicaItem ingrediente : ficha.getItens()) {
            try {
                // Calcular quantidade total a ser consumida
                BigDecimal quantidadeIngrediente = ingrediente.getQuantidade(); // Qtd por porção
                Integer quantidadeProduto = item.getQuantidade(); // Qtd de porções vendidas
                BigDecimal quantidadeTotal = quantidadeIngrediente.multiply(BigDecimal.valueOf(quantidadeProduto));

                // Criar movimento de estoque
                MovimentoEstoqueRequest movimentoRequest = MovimentoEstoqueRequest.builder()
                        .skuId(ingrediente.getInsumoSku().getId())
                        .tipoMovimento(TipoMovimentoEstoque.CONSUMO_PRODUCAO.getCodigo())
                        .quantidade(quantidadeTotal)
                        .observacao(String.format("Consumo: %dx %s (Pedido #%d)",
                                quantidadeProduto,
                                produto.getNome(),
                                item.getPedido().getId()))
                        .documentoReferencia("PEDIDO #" + item.getPedido().getId())
                        .itemPedidoId(item.getId())
                        .build();

                movimentoEstoqueService.movimentarEstoque(movimentoRequest);

                log.info("Baixa de {} {} do ingrediente {} processada com sucesso",
                        quantidadeTotal,
                        ingrediente.getInsumoSku().getProduto().getUnidadeBase() != null
                                ? ingrediente.getInsumoSku().getProduto().getUnidadeBase().name()
                                : "un",
                        ingrediente.getInsumoSku().getProduto().getNome());

            } catch (Exception e) {
                log.error("Erro ao processar baixa do ingrediente {} para produto {}: {}",
                        ingrediente.getInsumoSku().getProduto().getNome(),
                        produto.getNome(),
                        e.getMessage(), e);
                // Continua processando os outros ingredientes mesmo se um falhar
            }
        }

        log.info("Baixa de insumos concluída para produto {} (total: {} ingredientes)",
                produto.getNome(), ficha.getItens().size());
    }

    /**
     * Processa a baixa do estoque do SKU do item vendido (produtos vendáveis/estoque por SKU).
     * Regras:
     * - Não aplica para produtos INSUMO (controle é via estoque base, coberto por ficha técnica quando houver)
     * - Requer produto com controlaEstoque=true
     * - Usa o SKU do item; se ausente e o produto tiver exatamente 1 SKU, usa-o como fallback
     */
    @Transactional
    public void processarBaixaSku(ItemPedido item) {
        try {
            Produto produto = item.getProduto();
            if (produto == null) {
                log.warn("Item {} sem produto vinculado. Ignorando baixa de SKU.", item.getId());
                return;
            }

            // Ignorar se for insumo (controle centralizado) ou se não controla estoque
            if (Boolean.TRUE.equals(produto.getInsumo())) {
                log.debug("Produto {} é INSUMO. Baixa de SKU ignorada (controle centralizado).", produto.getNome());
                return;
            }
            if (produto.getControlaEstoque() == null || !produto.getControlaEstoque()) {
                log.debug("Produto {} não controla estoque. Baixa de SKU ignorada.", produto.getNome());
                return;
            }

            // Respeitar a segmentação: se produto tem ficha técnica, assume-se consumo por insumos; ainda assim
            // alguns itens vendáveis podem ter FT para custo. Para evitar dupla baixa, não debitar SKU quando tem FT.
            if (Boolean.TRUE.equals(produto.getTemFichaTecnica()) && !Boolean.TRUE.equals(produto.getProducaoPropria())) {
                log.debug("Produto {} possui ficha técnica. Evitando baixa dupla do SKU.", produto.getNome());
                return;
            }

            ProdutoSKU sku = item.getSku();
            if (sku == null) {
                // Fallback seguro: se o produto possuir exatamente 1 SKU, usar este
                if (produto.getSkus() != null && produto.getSkus().size() == 1) {
                    sku = produto.getSkus().get(0);
                }
            }
            if (sku == null) {
                log.warn("Item {} do produto {} não possui SKU definido e não foi possível inferir. Baixa de SKU ignorada.",
                        item.getId(), produto.getNome());
                return;
            }

            BigDecimal quantidade = BigDecimal.valueOf(item.getQuantidade() == null ? 0 : item.getQuantidade());
            if (quantidade.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Quantidade inválida para baixa de SKU no item {}. Ignorando.", item.getId());
                return;
            }

            MovimentoEstoqueRequest movimentoRequest = MovimentoEstoqueRequest.builder()
                    .skuId(sku.getId())
                    .tipoMovimento(TipoMovimentoEstoque.VENDA.getCodigo())
                    .quantidade(quantidade)
                    .observacao(String.format("Venda: %dx %s (Pedido #%d)",
                            item.getQuantidade(),
                            produto.getNome(),
                            item.getPedido() != null ? item.getPedido().getId() : null))
                    .documentoReferencia("PEDIDO #" + (item.getPedido() != null ? item.getPedido().getId() : ""))
                    .itemPedidoId(item.getId())
                    .build();

            movimentoEstoqueService.movimentarEstoque(movimentoRequest);

            log.info("Baixa de SKU concluída: SKU={}, Produto={}, Quantidade={}",
                    sku.getId(), produto.getNome(), quantidade);

        } catch (Exception e) {
            log.error("Erro ao processar baixa de SKU para item {}: {}", item.getId(), e.getMessage(), e);
        }
    }
}
