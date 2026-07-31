package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ContaReceber;
import com.baronesa.emporio.entity.ContaReceberParcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long>, JpaSpecificationExecutor<ContaReceber> {

    @Query("SELECT cr FROM ContaReceber cr LEFT JOIN FETCH cr.parcelas WHERE cr.id = :id")
    ContaReceber findByIdWithParcelas(@Param("id") Long id);

    @Query("SELECT DISTINCT cr FROM ContaReceber cr JOIN cr.parcelas p WHERE p.dataVencimento BETWEEN :inicio AND :fim")
    List<ContaReceber> findByVencimentoEntre(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    List<ContaReceber> findByClienteId(Long clienteId);

    // === QUERIES PARA DASHBOARD ===

    /**
     * Total de recebimentos com vencimento hoje (todas as parcelas, recebidas ou não)
     */
    @Query("""
        SELECT COALESCE(SUM(p.valor), 0)
        FROM ContaReceberParcela p
        WHERE p.dataVencimento = :hoje
    """)
    BigDecimal findValorVencimentoHoje(@Param("hoje") LocalDate hoje);

    /**
     * Total de recebimentos pendentes (vencidos)
     */
    @Query("""
        SELECT COALESCE(SUM(p.valor), 0)
        FROM ContaReceberParcela p
        WHERE p.dataVencimento < :hoje
        AND p.recebida = false
    """)
    BigDecimal findValorPendente(@Param("hoje") LocalDate hoje);

    /**
     * Total recebido hoje (usa valorRecebido se preenchido, senão usa valor)
     */
    @Query("""
        SELECT COALESCE(SUM(COALESCE(p.valorRecebido, p.valor)), 0)
        FROM ContaReceberParcela p
        WHERE p.dataRecebimento = :hoje
        AND p.recebida = true
    """)
    BigDecimal findTotalRecebidoHoje(@Param("hoje") LocalDate hoje);

    /**
     * Próximos recebimentos
     */
    @Query("""
        SELECT p
        FROM ContaReceberParcela p
        JOIN FETCH p.contaReceber cr
        JOIN FETCH cr.cliente
        WHERE p.dataVencimento BETWEEN :inicio AND :fim
        AND p.recebida = false
        ORDER BY p.dataVencimento
    """)
    List<ContaReceberParcela> findProximosRecebimentos(@Param("inicio") LocalDate inicio,
                                                       @Param("fim") LocalDate fim);

    boolean existsByNumeroDocumento(String numeroDocumento);
}
