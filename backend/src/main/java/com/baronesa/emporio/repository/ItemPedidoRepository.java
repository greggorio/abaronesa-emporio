package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ItemPedido;
import com.baronesa.emporio.entity.Pedido;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.enums.LocalPreparacao;
import com.baronesa.emporio.enums.StatusItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import com.baronesa.emporio.dto.ProdutoFrequenteDTO;
import org.springframework.data.domain.Pageable;

@Repository
public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {
    List<ItemPedido> findByPedido(Pedido pedido);
    List<ItemPedido> findByPedido_SessaoMesa(SessaoMesa sessaoMesa);
    List<ItemPedido> findByPedido_SessaoConvidado(SessaoConvidado sessaoConvidado);
    List<ItemPedido> findByPedido_SessaoConvidadoIn(List<SessaoConvidado> convidados);
    List<ItemPedido> findByStatus(StatusItem status);

    // Métodos para dashboard
    Long countByProdutoLocalPreparacaoAndStatus(LocalPreparacao localPreparacao, StatusItem status);
    Long countByStatus(StatusItem status);

    @Query("""
        SELECT
            new com.baronesa.emporio.dto.ProdutoFrequenteDTO(
                ip.produto.id,
                ip.produto.nome,
                ip.produto.imagemPrincipal,
                SUM(ip.quantidade),
                MAX(ip.pedido.criadoEm)
            )
        FROM ItemPedido ip
        WHERE ip.pedido.sessaoConvidado.usuario.id = :usuarioId
        AND ip.status != com.baronesa.emporio.enums.StatusItem.CANCELED
        GROUP BY ip.produto.id, ip.produto.nome, ip.produto.imagemPrincipal
        ORDER BY SUM(ip.quantidade) DESC
    """)
    List<ProdutoFrequenteDTO> findFavoritosByUsuarioId(@Param("usuarioId") Long usuarioId, Pageable pageable);

    /**
     * Busca produtos com melhor desempenho ordenados por valor total
     * Considera apenas produtos de sessões que tiveram pagamento efetivado
     * Retorna: [produto_id, nome, quantidade_total, valor_total]
     */
    @Query(value = """
        SELECT
            p.id as produto_id,
            p.nome,
            COALESCE(SUM(ip.quantidade), 0) as quantidade_total,
            COALESCE(SUM(ip.preco_unitario * ip.quantidade), 0) as valor_total
        FROM item_pedido ip
        INNER JOIN pedido ped ON ip.pedido_id = ped.id
        INNER JOIN produto p ON ip.produto_id = p.id
        WHERE EXISTS (
            SELECT 1 FROM pagamento pag
            WHERE pag.sessao_mesa_id = ped.sessao_mesa_id
            AND pag.status = 'PAID'
            AND pag.pago_em >= :startDate
            AND pag.pago_em < :endDate
        )
        GROUP BY p.id, p.nome
        ORDER BY valor_total DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Object[]> findTopProdutosByValor(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Busca produtos com melhor desempenho ordenados por quantidade vendida
     * Considera apenas produtos de sessões que tiveram pagamento efetivado
     * Retorna: [produto_id, nome, quantidade_total, valor_total]
     */
    @Query(value = """
        SELECT
            p.id as produto_id,
            p.nome,
            COALESCE(SUM(ip.quantidade), 0) as quantidade_total,
            COALESCE(SUM(ip.preco_unitario * ip.quantidade), 0) as valor_total
        FROM item_pedido ip
        INNER JOIN pedido ped ON ip.pedido_id = ped.id
        INNER JOIN produto p ON ip.produto_id = p.id
        WHERE EXISTS (
            SELECT 1 FROM pagamento pag
            WHERE pag.sessao_mesa_id = ped.sessao_mesa_id
            AND pag.status = 'PAID'
            AND pag.pago_em >= :startDate
            AND pag.pago_em < :endDate
        )
        GROUP BY p.id, p.nome
        ORDER BY quantidade_total DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Object[]> findTopProdutosByQuantidade(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("""
        SELECT ip
        FROM ItemPedido ip
        WHERE ip.status = com.baronesa.emporio.enums.StatusItem.CANCELED
          AND ip.pedido.criadoEm >= :inicio
          AND ip.pedido.criadoEm < :fim
        ORDER BY ip.pedido.criadoEm DESC
    """)
    List<ItemPedido> findCanceledBetween(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    List<ItemPedido> findByPedido_SessaoMesaId(Long sessaoMesaId);

    List<ItemPedido> findByPedido_SessaoMesaAndStatus(SessaoMesa sessaoMesa, StatusItem status);

    List<ItemPedido> findByPedido_SessaoMesaIdAndStatus(Long sessaoMesaId, StatusItem status);

    boolean existsBySkuId(Long skuId);

    List<ItemPedido> findByPedido_SessaoConvidadoIdInAndStatus(java.util.Collection<Long> sessaoConvidadoIds, StatusItem status);

    /**
     * Consulta para obter dados de vendas por produtos em promoção nos últimos 7 dias
     * Retorna: [produto_id, total_vendido, quantidade_total]
     */
    @Query(value = """
        SELECT
            ip.produto_id as produtoId,
            SUM(ip.preco_unitario * ip.quantidade) as totalVendido,
            SUM(ip.quantidade) as quantidadeTotal
        FROM item_pedido ip
        JOIN pedido p ON ip.pedido_id = p.id
        WHERE ip.produto_id IN :produtoIds
        AND EXISTS (
            SELECT 1 FROM pagamento pag
            WHERE pag.sessao_mesa_id = p.sessao_mesa_id
            AND pag.status = 'PAID'
            AND pag.pago_em >= :startDate
            AND pag.pago_em < :endDate
        )
        GROUP BY ip.produto_id
        """, nativeQuery = true)
    List<Object[]> findVendasByProdutoIds(@Param("produtoIds") List<Long> produtoIds,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    /**
     * Consulta para obter o impacto financeiro das promoções (diferença entre preço original e preço vendido)
     * Retorna: [produto_id, impacto_total]
     */
    @Query(value = """
        SELECT
            ip.produto_id as produtoId,
            SUM((COALESCE(s.preco_venda, p.preco_venda) - ip.preco_unitario) * ip.quantidade) as impactoTotal
        FROM item_pedido ip
        JOIN produto p ON ip.produto_id = p.id
        LEFT JOIN produto_sku s ON ip.sku_id = s.id
        JOIN pedido ped ON ip.pedido_id = ped.id
        WHERE ip.produto_id IN :produtoIds
        AND EXISTS (
            SELECT 1 FROM pagamento pag
            WHERE pag.sessao_mesa_id = ped.sessao_mesa_id
            AND pag.status = 'PAID'
            AND pag.pago_em >= :startDate
            AND pag.pago_em < :endDate
        )
        GROUP BY ip.produto_id
        """, nativeQuery = true)
    List<Object[]> findImpactoVendasByProdutoIds(@Param("produtoIds") List<Long> produtoIds,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Consulta para obter contagem de vendas promocionais e normais por produtos
     * Retorna: [produto_id, quantidade_promocional, quantidade_normal]
     */
    @Query(value = """
        SELECT
            ip.produto_id as produtoId,
            SUM(CASE WHEN ip.preco_unitario < COALESCE(sku.preco_venda, p.preco_venda) THEN ip.quantidade ELSE 0 END) as quantidadePromocional,
            SUM(CASE WHEN ip.preco_unitario >= COALESCE(sku.preco_venda, p.preco_venda) THEN ip.quantidade ELSE 0 END) as quantidadeNormal
        FROM item_pedido ip
        JOIN produto p ON ip.produto_id = p.id
        LEFT JOIN produto_sku sku ON ip.sku_id = sku.id
        JOIN pedido ped ON ip.pedido_id = ped.id
        WHERE ip.produto_id IN :produtoIds
        AND EXISTS (
            SELECT 1 FROM pagamento pag
            WHERE pag.sessao_mesa_id = ped.sessao_mesa_id
            AND pag.status = 'PAID'
            AND pag.pago_em >= :startDate
            AND pag.pago_em < :endDate
        )
        GROUP BY ip.produto_id
        """, nativeQuery = true)
    List<Object[]> findVendasPromocionaisNormaisByProdutoIds(@Param("produtoIds") List<Long> produtoIds,
                                                             @Param("startDate") LocalDateTime startDate,
                                                             @Param("endDate") LocalDateTime endDate);

    /**
     * Busca vendas agrupadas por produto para relatório
     * Retorna: [produto_id, nome, quantidade_total, valor_total, preco_unitario_medio]
     */
    @Query(value = """
        SELECT
            p.id as produto_id,
            p.nome,
            COALESCE(SUM(ip.quantidade), 0) as quantidade_total,
            COALESCE(SUM(ip.preco_unitario * ip.quantidade), 0) as valor_total,
            CASE WHEN SUM(ip.quantidade) > 0
                THEN COALESCE(SUM(ip.preco_unitario * ip.quantidade), 0) / SUM(ip.quantidade)
                ELSE 0
            END as preco_unitario_medio
        FROM item_pedido ip
        INNER JOIN pedido ped ON ip.pedido_id = ped.id
        INNER JOIN produto p ON ip.produto_id = p.id
        WHERE EXISTS (
            SELECT 1 FROM pagamento pag
            WHERE pag.sessao_mesa_id = ped.sessao_mesa_id
            AND pag.status = 'PAID'
            AND pag.pago_em >= :startDate
            AND pag.pago_em < :endDate
        )
        AND (:produtoId IS NULL OR p.id = :produtoId)
        GROUP BY p.id, p.nome
        ORDER BY valor_total DESC
        """, nativeQuery = true)
    List<Object[]> findVendasPorProduto(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate,
            @Param("produtoId") Long produtoId);

    @Query(value = """
        SELECT 
            pag.pago_em as data_hora,
            pag.id as cupom_id,
            p.nome as produto_nome,
            ip.quantidade,
            ip.preco_unitario,
            (ip.quantidade * ip.preco_unitario) as valor_total
        FROM item_pedido ip
        INNER JOIN pedido ped ON ip.pedido_id = ped.id
        INNER JOIN produto p ON ip.produto_id = p.id
        INNER JOIN pagamento pag ON pag.sessao_mesa_id = ped.sessao_mesa_id
        WHERE pag.status = 'PAID'
          AND pag.pago_em >= :startDate 
          AND pag.pago_em < :endDate
          AND (:produtoId IS NULL OR p.id = :produtoId)
        ORDER BY pag.pago_em DESC
        """, nativeQuery = true)
    List<Object[]> findVendasAnalitico(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate,
            @Param("produtoId") Long produtoId);
}
