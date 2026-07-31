
package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.RecebimentoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecebimentoItemRepository extends JpaRepository<RecebimentoItem, Long> {

    // Buscar itens por recebimento
    List<RecebimentoItem> findByRecebimentoId(Long recebimentoId);

    // Buscar itens por produto
    List<RecebimentoItem> findByProdutoId(Long produtoId);

    // Buscar itens por lote
    List<RecebimentoItem> findByLote(String lote);

    // Query para buscar itens com produto
    @Query("SELECT i FROM RecebimentoItem i " +
            "JOIN FETCH i.produto " +
            "WHERE i.recebimento.id = :recebimentoId")
    List<RecebimentoItem> findByRecebimentoIdWithProduto(@Param("recebimentoId") Long recebimentoId);

    // Deletar itens por recebimento
    void deleteByRecebimentoId(Long recebimentoId);

    // Verificar se produto está em algum recebimento pendente
    @Query("SELECT COUNT(i) > 0 FROM RecebimentoItem i " +
            "WHERE i.produto.id = :produtoId " +
            "AND i.recebimento.status = 'PENDENTE'")
    boolean existsByProdutoIdInRecebimentoPendente(@Param("produtoId") Long produtoId);
}