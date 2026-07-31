package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ProdutoSKU;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProdutoSKURepository extends JpaRepository<ProdutoSKU, Long> {

    @Query("SELECT sku FROM ProdutoSKU sku WHERE sku.estoque.quantidade > 0")
    List<ProdutoSKU> findSkusComEstoque();

    @Query("SELECT sku FROM ProdutoSKU sku WHERE sku.estoque IS NOT NULL AND sku.estoque.quantidade != 0")
    List<ProdutoSKU> findSkusComEstoqueDiferenteZero();

    List<ProdutoSKU> findByProdutoIdAndEmbalagemId(Long produtoId, Long embalagemId);

    @Query("SELECT s FROM ProdutoSKU s " +
           "LEFT JOIN FETCH s.produto p " +
           "LEFT JOIN FETCH s.embalagem e " +
           "LEFT JOIN FETCH p.embalagens " +
           "WHERE s.id = :id")
    Optional<ProdutoSKU> findByIdWithProduto(@Param("id") Long id);

    @Query("""
           SELECT DISTINCT s FROM ProdutoSKU s
           LEFT JOIN FETCH s.produto p
           LEFT JOIN FETCH s.estoque e
           WHERE p.id = :produtoId
           ORDER BY s.id ASC
           """)
    List<ProdutoSKU> findByProdutoIdWithProdutoAndEstoque(@Param("produtoId") Long produtoId);
}
