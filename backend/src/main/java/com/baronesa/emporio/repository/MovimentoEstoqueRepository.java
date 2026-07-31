package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.MovimentoEstoque;
import com.baronesa.emporio.entity.ProdutoSKU;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long>,
        JpaSpecificationExecutor<MovimentoEstoque> {

    // Buscar movimentos por SKU
    Page<MovimentoEstoque> findBySkuIdOrderByDataMovimentoDesc(Long skuId, Pageable pageable);

    // Buscar último movimento de um SKU
    Optional<MovimentoEstoque> findTopBySkuIdOrderByIdDesc(Long skuId);

    // Buscar movimentos por tipo
    List<MovimentoEstoque> findByTipoMovimentoAndDataMovimentoBetween(
            Integer tipoMovimento,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    // Buscar movimentos por referência de venda
    List<MovimentoEstoque> findByVendaId(Long vendaId);

    // Buscar movimentos por referência de recebimento
    List<MovimentoEstoque> findByRecebimentoId(Long recebimentoId);

    // Buscar movimentos por referência de consignação
    List<MovimentoEstoque> findByConsignacaoId(Long consignacaoId);

    // Buscar movimentos por item do pedido
    List<MovimentoEstoque> findByItemPedidoIdOrderByDataMovimentoDesc(Long itemPedidoId);

    // Buscar movimentos por item do pedido (sem ordenação específica)
    List<MovimentoEstoque> findByItemPedidoId(Long itemPedidoId);

    // Buscar movimentos originais por item do pedido (onde movimento_origem_id IS NULL)
    @Query("SELECT m FROM MovimentoEstoque m WHERE m.itemPedidoId = :itemPedidoId AND m.movimentoOrigemId IS NULL")
    List<MovimentoEstoque> findByItemPedidoIdAndMovimentoOrigemIdIsNull(Long itemPedidoId);

    // Buscar movimentos por movimento origem (estornos)
    List<MovimentoEstoque> findByMovimentoOrigemId(Long movimentoOrigemId);

    // Verificar se existe estorno para um movimento
    boolean existsByMovimentoOrigemId(Long movimentoOrigemId);

    // Query customizada para relatório de movimentações
    @Query("SELECT m FROM MovimentoEstoque m " +
            "JOIN FETCH m.sku s " +
            "JOIN FETCH s.produto p " +
            "WHERE m.dataMovimento BETWEEN :inicio AND :fim " +
            "ORDER BY m.dataMovimento DESC")
    List<MovimentoEstoque> findMovimentosPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    // Verificar se existe movimento inicial para o SKU
    @Query("SELECT COUNT(m) > 0 FROM MovimentoEstoque m " +
            "WHERE m.sku.id = :skuId AND m.tipoMovimento = 0")
    boolean existsMovimentoInicialBySku(@Param("skuId") Long skuId);

    // Buscar movimentos com estoque negativo
    @Query("SELECT m FROM MovimentoEstoque m " +
            "WHERE m.estoqueAtual < 0 " +
            "ORDER BY m.dataMovimento DESC")
    List<MovimentoEstoque> findMovimentosComEstoqueNegativo();

    // Estatísticas de movimentação por período
    @Query("SELECT m.tipoMovimento, COUNT(m), SUM(m.quantidade) " +
            "FROM MovimentoEstoque m " +
            "WHERE m.dataMovimento BETWEEN :inicio AND :fim " +
            "GROUP BY m.tipoMovimento")
    List<Object[]> getEstatisticasMovimentacao(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );
}