package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.EstoqueLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EstoqueLoteRepository extends JpaRepository<EstoqueLote, Long> {
    boolean existsByProdutoSkuProdutoId(Long produtoId);

    @Query("""
        SELECT el FROM EstoqueLote el
        WHERE el.produtoSku.id = :produtoSkuId
          AND COALESCE(el.lote, '') = :loteNormalizado
          AND COALESCE(el.dataValidade, :defaultDataValidade) = :dataValidadeNormalizada
    """)
    Optional<EstoqueLote> findBySkuLoteValidadeNullable(@Param("produtoSkuId") Long produtoSkuId,
                                                        @Param("loteNormalizado") String loteNormalizado,
                                                        @Param("dataValidadeNormalizada") LocalDate dataValidadeNormalizada,
                                                        @Param("defaultDataValidade") LocalDate defaultDataValidade);

    @Query("""
        SELECT el FROM EstoqueLote el
        WHERE el.produtoSku.id = :produtoSkuId
        ORDER BY el.dataValidade ASC NULLS LAST, el.id ASC
    """)
    List<EstoqueLote> findByProdutoSkuIdOrderByDataValidadeAscNullsLast(Long produtoSkuId);

    @Query("""
        SELECT el FROM EstoqueLote el
        LEFT JOIN FETCH el.produtoSku ps
        LEFT JOIN FETCH ps.produto p
        LEFT JOIN FETCH ps.estoque e
        WHERE p.id = :produtoId
        ORDER BY ps.id ASC, el.dataValidade ASC NULLS LAST, el.id ASC
    """)
    List<EstoqueLote> findByProdutoIdOrderBySkuAndDataValidadeAscNullsLast(Long produtoId);

    @Query("SELECT el FROM EstoqueLote el " +
           "LEFT JOIN el.produtoSku ps " +
           "LEFT JOIN ps.produto p " +
           "LEFT JOIN ps.estoque e " +
           "WHERE (:somenteComSaldo = false OR (el.quantidade > 0 AND COALESCE(e.quantidade, 0) > 0)) " +
           "AND (:skuId IS NULL OR ps.id = :skuId) " +
           "AND (:produtoId IS NULL OR p.id = :produtoId) " +
           "ORDER BY el.dataValidade ASC, el.id ASC")
    List<EstoqueLote> findAlertasValidade(
        @Param("somenteComSaldo") Boolean somenteComSaldo,
        @Param("skuId") Long skuId,
        @Param("produtoId") Long produtoId
    );
}
