package com.baronesa.emporio.repository;

import com.baronesa.emporio.dto.dashboard.MovimentoCaixaResumoDTO;
import com.baronesa.emporio.entity.MovimentoCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface MovimentoCaixaRepository extends
        JpaRepository<MovimentoCaixa, Long>,
        JpaSpecificationExecutor<MovimentoCaixa> {

    /**
     * Busca resumo do movimento de caixa (entradas, saídas e saldo)
     * Usando parâmetros de data - VERSÃO NATIVA MAIS ROBUSTA
     */
    @Query(value = """
        SELECT 
            CAST(COALESCE(SUM(CASE WHEN operacao = 'ENTRADA' THEN valor ELSE 0 END), 0) AS DECIMAL) as entradas,
            CAST(COALESCE(SUM(CASE WHEN operacao = 'SAIDA' THEN valor ELSE 0 END), 0) AS DECIMAL) as saidas,
            CAST(COALESCE(SUM(CASE WHEN operacao = 'ENTRADA' THEN valor ELSE -valor END), 0) AS DECIMAL) as saldo
        FROM movimentos_caixa
        WHERE afeta_caixa = true
        AND data_hora >= :inicio
        AND data_hora <= :fim
    """, nativeQuery = true)
    Object[] findResumoHojeRaw(@Param("inicio") LocalDateTime inicio,
                               @Param("fim") LocalDateTime fim);

    /**
     * Busca detalhes por tipo de pagamento
     * Usando parâmetros de data - VERSÃO NATIVA
     */
    @Query(value = """
        SELECT meio_pagamento, SUM(valor)
        FROM movimentos_caixa
        WHERE data_hora >= :inicio
        AND data_hora <= :fim
        AND afeta_caixa = true
        AND operacao = 'ENTRADA'
        GROUP BY meio_pagamento
    """, nativeQuery = true)
    List<Object[]> findDetalhesPorTipoRaw(@Param("inicio") LocalDateTime inicio,
                                          @Param("fim") LocalDateTime fim);

    /**
     * Busca resumo usando CURRENT_DATE diretamente (evita problemas de timezone)
     */
    @Query(value = """
        SELECT 
            COALESCE(SUM(CASE WHEN m.operacao = 'ENTRADA' THEN m.valor ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN m.operacao = 'SAIDA' THEN m.valor ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN m.operacao = 'ENTRADA' THEN m.valor ELSE -m.valor END), 0)
        FROM movimentos_caixa m
        WHERE m.afeta_caixa = true
        AND m.data_hora >= CURRENT_DATE
        AND m.data_hora < CURRENT_DATE + INTERVAL '1 day'
    """, nativeQuery = true)
    Object[] findResumoHojeCurrentDate();

    /**
     * Busca detalhes por tipo usando CURRENT_DATE diretamente
     */
    @Query(value = """
        SELECT m.meio_pagamento, SUM(m.valor)
        FROM movimentos_caixa m
        WHERE m.afeta_caixa = true
        AND m.operacao = 'ENTRADA'
        AND m.data_hora >= CURRENT_DATE
        AND m.data_hora < CURRENT_DATE + INTERVAL '1 day'
        GROUP BY m.meio_pagamento
    """, nativeQuery = true)
    List<Object[]> findDetalhesPorTipoCurrentDate();

    /**
     * Método principal que usa LocalDate como parâmetro
     */
    default MovimentoCaixaResumoDTO findResumoHoje(LocalDate data) {
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.atTime(23, 59, 59);

        Object[] resumo = findResumoHojeRaw(inicio, fim);
        List<Object[]> detalhes = findDetalhesPorTipoRaw(inicio, fim);

        if (resumo == null || resumo.length == 0) {
            return new MovimentoCaixaResumoDTO(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new java.util.HashMap<>()
            );
        }

        // Log para debug
        System.out.println("DEBUG - Tamanho do array resumo: " + resumo.length);

        // Verificar se é um array aninhado (PostgreSQL às vezes retorna assim)
        if (resumo.length == 1 && resumo[0] instanceof Object[]) {
            System.out.println("DEBUG - Array aninhado detectado, desembrulhando...");
            resumo = (Object[]) resumo[0];
            System.out.println("DEBUG - Novo tamanho após desembrulhar: " + resumo.length);
        }

        for (int i = 0; i < resumo.length; i++) {
            System.out.println("DEBUG - resumo[" + i + "] = " + resumo[i]);
        }

        Map<String, BigDecimal> detalhesPorTipo = new java.util.HashMap<>();
        for (Object[] detalhe : detalhes) {
            if (detalhe[0] != null && detalhe[1] != null) {
                String tipo = detalhe[0].toString();
                BigDecimal valor = convertToBigDecimal(detalhe[1]);
                detalhesPorTipo.put(tipo, valor);
            }
        }

        // Verificar se o array tem todos os elementos esperados
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas = BigDecimal.ZERO;
        BigDecimal saldo = BigDecimal.ZERO;

        if (resumo.length >= 1) {
            entradas = convertToBigDecimal(resumo[0]);
        }
        if (resumo.length >= 2) {
            saidas = convertToBigDecimal(resumo[1]);
        }
        if (resumo.length >= 3) {
            saldo = convertToBigDecimal(resumo[2]);
        }

        return new MovimentoCaixaResumoDTO(entradas, saidas, saldo, detalhesPorTipo);
    }

    /**
     * Método alternativo que usa CURRENT_DATE do banco
     * Útil quando há problemas de timezone entre JVM e banco
     */
    default MovimentoCaixaResumoDTO findResumoHojeUsandoCurrentDate() {
        Object[] resumo = findResumoHojeCurrentDate();
        List<Object[]> detalhes = findDetalhesPorTipoCurrentDate();

        if (resumo == null || resumo.length == 0) {
            return new MovimentoCaixaResumoDTO(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new java.util.HashMap<>()
            );
        }

        // Log para debug
        System.out.println("DEBUG CURRENT_DATE - Tamanho do array resumo: " + resumo.length);

        // Verificar se é um array aninhado (PostgreSQL às vezes retorna assim)
        if (resumo.length == 1 && resumo[0] instanceof Object[]) {
            System.out.println("DEBUG CURRENT_DATE - Array aninhado detectado, desembrulhando...");
            resumo = (Object[]) resumo[0];
            System.out.println("DEBUG CURRENT_DATE - Novo tamanho após desembrulhar: " + resumo.length);
        }

        for (int i = 0; i < resumo.length; i++) {
            System.out.println("DEBUG CURRENT_DATE - resumo[" + i + "] = " + resumo[i] + " (tipo: " + (resumo[i] != null ? resumo[i].getClass().getName() : "null") + ")");
        }

        Map<String, BigDecimal> detalhesPorTipo = new java.util.HashMap<>();
        for (Object[] detalhe : detalhes) {
            if (detalhe[0] != null && detalhe[1] != null) {
                String tipo = detalhe[0].toString();
                BigDecimal valor = convertToBigDecimal(detalhe[1]);
                detalhesPorTipo.put(tipo, valor);
            }
        }

        // Verificar se o array tem todos os elementos esperados
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas = BigDecimal.ZERO;
        BigDecimal saldo = BigDecimal.ZERO;

        if (resumo.length >= 1) {
            entradas = convertToBigDecimal(resumo[0]);
        }
        if (resumo.length >= 2) {
            saidas = convertToBigDecimal(resumo[1]);
        }
        if (resumo.length >= 3) {
            saldo = convertToBigDecimal(resumo[2]);
        }

        return new MovimentoCaixaResumoDTO(entradas, saidas, saldo, detalhesPorTipo);
    }

    /**
     * Método utilitário para converter valores do banco para BigDecimal
     */
    private static BigDecimal convertToBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Busca movimentos por período
     */
    @Query("""
        SELECT m FROM MovimentoCaixa m
        WHERE m.dataHora BETWEEN :inicio AND :fim
        ORDER BY m.dataHora DESC
    """)
    List<MovimentoCaixa> findByPeriodo(@Param("inicio") LocalDateTime inicio,
                                       @Param("fim") LocalDateTime fim);

    /**
     * Busca movimentos que afetam o caixa por período
     */
    @Query("""
        SELECT m FROM MovimentoCaixa m
        WHERE m.afetaCaixa = true
        AND m.dataHora BETWEEN :inicio AND :fim
        ORDER BY m.dataHora DESC
    """)
    List<MovimentoCaixa> findMovimentosAfetamCaixaPorPeriodo(@Param("inicio") LocalDateTime inicio,
                                                             @Param("fim") LocalDateTime fim);

    /**
     * Calcula o saldo acumulado até uma determinada data
     */
    @Query("""
    SELECT COALESCE(SUM(
        CASE 
            WHEN m.operacao = 'ENTRADA' THEN m.valor 
            ELSE -m.valor 
        END
    ), 0)
    FROM MovimentoCaixa m
    WHERE m.afetaCaixa = true
    AND m.dataHora < :dataLimite
""")
    BigDecimal calcularSaldoAteData(@Param("dataLimite") LocalDateTime dataLimite);
}