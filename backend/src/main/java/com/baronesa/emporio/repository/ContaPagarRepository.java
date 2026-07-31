package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ContaPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long>, JpaSpecificationExecutor<ContaPagar> {

    @Query("SELECT cp FROM ContaPagar cp LEFT JOIN FETCH cp.parcelas WHERE cp.id = :id")
    ContaPagar findByIdWithParcelas(@Param("id") Long id);

    // === QUERIES PARA DASHBOARD ===

    /**
     * Total de pagamentos com vencimento hoje (todas as parcelas, pagas ou não)
     */
    @Query("""
        SELECT COALESCE(SUM(p.valor), 0)
        FROM ContaPagarParcela p
        WHERE p.dataVencimento = :hoje
    """)
    BigDecimal findValorVencimentoHoje(@Param("hoje") LocalDate hoje);

    /**
     * Total de pagamentos pendentes (vencidos)
     */
    @Query("""
        SELECT COALESCE(SUM(p.valor), 0)
        FROM ContaPagarParcela p
        WHERE p.dataVencimento < :hoje
        AND p.paga = false
    """)
    BigDecimal findValorPendente(@Param("hoje") LocalDate hoje);

    /**
     * Total pago hoje
     */
    @Query("""
        SELECT COALESCE(SUM(p.valor), 0)
        FROM ContaPagarParcela p
        WHERE p.dataPagamento = :hoje
        AND p.paga = true
    """)
    BigDecimal findTotalPagoHoje(@Param("hoje") LocalDate hoje);
}
