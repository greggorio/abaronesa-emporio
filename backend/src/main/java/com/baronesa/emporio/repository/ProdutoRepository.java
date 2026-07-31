package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.enums.TipoPrecificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long>, JpaSpecificationExecutor<Produto> {

    Optional<Produto> findByCodigoInterno(String codigoInterno);

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    Optional<Produto> findByCodigoFornecedor(String codigoFornecedor);

    List<Produto> findByCategoriaId(Long categoriaId);

    List<Produto> findBySubcategoriaId(Long subcategoriaId);

    List<Produto> findByFornecedorId(Long fornecedorId);

    List<Produto> findByAtivoTrue();

    List<Produto> findByExibirNoCardapioTrue();

    List<Produto> findByPromocaoTrue();

    List<Produto> findByDestaqueTrue();

    @Query("SELECT p FROM Produto p WHERE p.ativo = true ORDER BY p.nome")
    List<Produto> findAllAtivos();

    @Query("SELECT p FROM Produto p WHERE LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%')) " +
           "OR LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%'))")
    List<Produto> buscarPorTermo(@Param("termo") String termo);

    boolean existsByCodigoInterno(String codigoInterno);

    boolean existsByCodigoBarras(String codigoBarras);

    /**
     * Conta produtos com precificação SIMPLES/UNIFICADA sem preço de venda definido
     */
    @Query("""
            SELECT COUNT(p) FROM Produto p
            WHERE p.tipoPrecificacao IN :tipos
              AND (p.precoVenda IS NULL OR p.precoVenda <= 0)
              AND (:apenasAtivos = false OR (p.ativo = true AND p.vendavel = true))
            """)
    Long countProdutosSemPrecoUnificado(@Param("tipos") List<TipoPrecificacao> tipos,
                                        @Param("apenasAtivos") boolean apenasAtivos);

    /**
     * Conta produtos com precificação INDIVIDUAL que não possuem nenhum SKU ativo com preço de venda
     */
    @Query("""
            SELECT COUNT(DISTINCT p) FROM Produto p
            WHERE p.tipoPrecificacao = :tipo
              AND (:apenasAtivos = false OR (p.ativo = true AND p.vendavel = true))
              AND NOT EXISTS (
                  SELECT s FROM ProdutoSKU s
                  WHERE s.produto = p
                    AND (:apenasAtivos = false OR s.ativo = true)
                    AND s.precoVenda IS NOT NULL AND s.precoVenda > 0
              )
            """)
    Long countProdutosIndividuaisSemPreco(@Param("tipo") TipoPrecificacao tipo,
                                          @Param("apenasAtivos") boolean apenasAtivos);

    /**
     * Conta produtos sem estoque disponível (considera insumos com estoque centralizado e demais SKUs)
     */
    @Query("""
            SELECT COUNT(DISTINCT p) FROM Produto p
            LEFT JOIN p.estoqueProduto ep
            WHERE (:apenasAtivos = false OR (p.ativo = true AND p.vendavel = true))
              AND COALESCE(p.controlaEstoque, true) = true
              AND (
                  (p.insumo = true AND (ep IS NULL OR COALESCE(ep.quantidadeBase, 0) <= 0))
                  OR
                  (COALESCE(p.insumo, false) = false AND NOT EXISTS (
                      SELECT s FROM ProdutoSKU s
                      LEFT JOIN s.estoque e
                      WHERE s.produto = p
                        AND (:apenasAtivos = false OR s.ativo = true)
                        AND COALESCE(e.quantidade, 0) > 0
                  ))
              )
            """)
    Long countProdutosSemEstoque(@Param("apenasAtivos") boolean apenasAtivos);

    /**
     * Lista produtos sem preço seguindo as mesmas regras dos contadores
     */
    @Query("""
            SELECT DISTINCT p FROM Produto p
            WHERE (
                (p.tipoPrecificacao IN :tipos AND (p.precoVenda IS NULL OR p.precoVenda <= 0)
                    AND (:apenasAtivos = false OR (p.ativo = true AND p.vendavel = true)))
                OR (
                    p.tipoPrecificacao = :tipoIndividual
                    AND (:apenasAtivos = false OR (p.ativo = true AND p.vendavel = true))
                    AND NOT EXISTS (
                        SELECT s FROM ProdutoSKU s
                        WHERE s.produto = p
                          AND (:apenasAtivos = false OR s.ativo = true)
                          AND s.precoVenda IS NOT NULL AND s.precoVenda > 0
                    )
                )
            )
            """)
    Page<Produto> findProdutosSemPreco(@Param("tipos") List<TipoPrecificacao> tipos,
                                       @Param("tipoIndividual") TipoPrecificacao tipoIndividual,
                                       @Param("apenasAtivos") boolean apenasAtivos,
                                       Pageable pageable);

    /**
     * Lista produtos sem estoque disponível seguindo as mesmas regras dos contadores
     */
    @Query("""
            SELECT DISTINCT p FROM Produto p
            LEFT JOIN p.estoqueProduto ep
            WHERE (:apenasAtivos = false OR (p.ativo = true AND p.vendavel = true))
              AND COALESCE(p.controlaEstoque, true) = true
              AND (
                  (p.insumo = true AND (ep IS NULL OR COALESCE(ep.quantidadeBase, 0) <= 0))
                  OR
                  (COALESCE(p.insumo, false) = false AND NOT EXISTS (
                      SELECT s FROM ProdutoSKU s
                      LEFT JOIN s.estoque e
                      WHERE s.produto = p
                        AND (:apenasAtivos = false OR s.ativo = true)
                        AND COALESCE(e.quantidade, 0) > 0
                  ))
              )
            """)
    Page<Produto> findProdutosSemEstoque(@Param("apenasAtivos") boolean apenasAtivos,
                                         Pageable pageable);
}
