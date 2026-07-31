package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ProdutoPromocao;
import com.baronesa.emporio.enums.DiaSemana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Repository
public interface ProdutoPromocaoRepository extends JpaRepository<ProdutoPromocao, Long> {
    List<ProdutoPromocao> findByProdutoId(Long produtoId);
    List<ProdutoPromocao> findByProdutoIdAndAtivoTrue(Long produtoId);
    List<ProdutoPromocao> findByProdutoIdIn(Set<Long> produtoIds);

    @Query("""
        SELECT pp FROM ProdutoPromocao pp
        WHERE pp.produto.id = :produtoId
          AND pp.diaSemana = :diaSemana
          AND (:excluirId IS NULL OR pp.id != :excluirId)
          AND pp.horarioInicio < :horarioFim
          AND pp.horarioFim > :horarioInicio
        """)
    List<ProdutoPromocao> findSobreposicao(
            @Param("produtoId") Long produtoId,
            @Param("diaSemana") DiaSemana diaSemana,
            @Param("horarioInicio") LocalTime horarioInicio,
            @Param("horarioFim") LocalTime horarioFim,
            @Param("excluirId") Long excluirId
    );

    @Query("SELECT COUNT(DISTINCT pp.produto.id) FROM ProdutoPromocao pp WHERE pp.ativo = true")
    Long countDistinctProdutosAtivos();

    @Query("SELECT DISTINCT pp.produto FROM ProdutoPromocao pp WHERE pp.ativo = true")
    List<com.baronesa.emporio.entity.Produto> findDistinctProdutosAtivos();

    /**
     * Consulta para obter o desconto efetivo de promoções ativas por produto
     * Retorna: [produto_id, tipo_promocao, percentual_desconto, valor_promocional, preco_base_produto, preco_base_sku_principal]
     * Considera a regra de desempate: maior desconto efetivo por produto
     */
    @Query(value = """
        SELECT
            pp.produto_id as produtoId,
            pp.tipo_promocao as tipoPromocao,
            pp.percentual_desconto as percentualDesconto,
            pp.valor_promocional as valorPromocional,
            p.preco_venda as precoBaseProduto,
            ps.preco_venda as precoBaseSkuPrincipal
        FROM produto_promocao pp
        JOIN produto p ON pp.produto_id = p.id
        LEFT JOIN produto_sku ps ON ps.produto_id = p.id AND ps.principal = true AND ps.ativo = true
        WHERE pp.ativo = true
        """, nativeQuery = true)
    List<Object[]> findPromocoesAtivasWithPrecos();

    /**
     * Consulta para obter o desconto efetivo de promoções ativas por produto considerando a regra de desempate
     * Retorna o desconto com maior valor efetivo para cada produto
     */
    @Query(value = """
        SELECT DISTINCT
            pp.produto_id as produtoId,
            CASE
                WHEN pp.tipo_promocao = 'PERCENTUAL' THEN pp.percentual_desconto
                WHEN pp.tipo_promocao = 'VALOR' AND COALESCE(ps.preco_venda, p.preco_venda) > 0 THEN
                    ((COALESCE(ps.preco_venda, p.preco_venda) - pp.valor_promocional) * 100) / COALESCE(ps.preco_venda, p.preco_venda)
                ELSE 0
            END as descontoPercentual
        FROM produto_promocao pp
        JOIN produto p ON pp.produto_id = p.id
        LEFT JOIN produto_sku ps ON ps.produto_id = p.id AND ps.principal = true AND ps.ativo = true
        WHERE pp.ativo = true
        AND pp.id IN (
            SELECT sub_pp.id FROM produto_promocao sub_pp
            JOIN produto sub_p ON sub_pp.produto_id = sub_p.id
            LEFT JOIN produto_sku sub_ps ON sub_ps.produto_id = sub_p.id AND sub_ps.principal = true AND sub_ps.ativo = true
            WHERE sub_pp.ativo = true
            AND sub_pp.produto_id = pp.produto_id
            ORDER BY
                CASE
                    WHEN sub_pp.tipo_promocao = 'PERCENTUAL' THEN sub_pp.percentual_desconto
                    WHEN sub_pp.tipo_promocao = 'VALOR' AND COALESCE(sub_ps.preco_venda, sub_p.preco_venda) > 0 THEN
                        ((COALESCE(sub_ps.preco_venda, sub_p.preco_venda) - sub_pp.valor_promocional) * 100) / COALESCE(sub_ps.preco_venda, sub_p.preco_venda)
                    ELSE 0
                END DESC
            LIMIT 1
        )
        """, nativeQuery = true)
    List<Object[]> findDescontoMedioByProduto();
}
