package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ContaReceberParcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ContaReceberParcelaRepository extends JpaRepository<ContaReceberParcela, Long>, JpaSpecificationExecutor<ContaReceberParcela> {

    List<ContaReceberParcela> findByContaReceberId(Long contaReceberId);

    @Query("SELECT p FROM ContaReceberParcela p WHERE p.dataVencimento <= :data AND p.recebida = false")
    List<ContaReceberParcela> findParcelasVencidas(@Param("data") LocalDate data);

    @Query("SELECT p FROM ContaReceberParcela p WHERE p.dataVencimento BETWEEN :inicio AND :fim")
    List<ContaReceberParcela> findByVencimentoEntre(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT p FROM ContaReceberParcela p WHERE p.cobrancaEnviada = false AND p.recebida = false AND p.dataVencimento <= :data")
    List<ContaReceberParcela> findParcelasParaCobranca(@Param("data") LocalDate data);

    @Query("SELECT p FROM ContaReceberParcela p " +
            "JOIN FETCH p.contaReceber cr " +
            "JOIN FETCH cr.cliente " +
            "WHERE p.dataRecebimento = :data " +
            "OR (p.dataVencimento = :data AND p.recebida = false) " +
            "ORDER BY p.recebida DESC, p.dataVencimento")
    List<ContaReceberParcela> findByDataRecebimentoOuVencimento(@Param("data") LocalDate data);
}