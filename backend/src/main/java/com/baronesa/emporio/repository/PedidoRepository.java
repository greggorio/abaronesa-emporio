package com.baronesa.emporio.repository;

import com.baronesa.emporio.dto.PedidosDiariosDTO;
import com.baronesa.emporio.entity.Pedido;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.entity.SessaoConvidado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findBySessaoMesa(SessaoMesa sessaoMesa);
    List<Pedido> findBySessaoConvidado(SessaoConvidado sessaoConvidado);

    @Query("SELECT COUNT(p) FROM Pedido p WHERE p.criadoEm >= :startDate AND p.criadoEm < :endDate")
    Long countPedidosByDate(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(i.precoUnitario * i.quantidade), 0) FROM ItemPedido i " +
           "WHERE i.pedido.criadoEm >= :startDate AND i.pedido.criadoEm < :endDate")
    Long sumTotalVendasByDate(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("""
        SELECT new com.baronesa.emporio.dto.PedidosDiariosDTO(
            CAST(p.criadoEm AS LocalDate),
            COALESCE(SUM(CASE WHEN pr.localPreparacao = 'BAR' THEN i.quantidade ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN pr.localPreparacao = 'COZINHA' THEN i.quantidade ELSE 0 END), 0)
        )
        FROM Pedido p
        JOIN p.itens i
        JOIN i.produto pr
        WHERE p.criadoEm >= :dataInicio
        AND pr.localPreparacao IN ('BAR', 'COZINHA')
        GROUP BY CAST(p.criadoEm AS LocalDate)
        ORDER BY CAST(p.criadoEm AS LocalDate) DESC
    """)
    List<PedidosDiariosDTO> findPedidosPorLocal(@Param("dataInicio") LocalDateTime dataInicio);
}

