package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ContaPagarParcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ContaPagarParcelaRepository extends JpaRepository<ContaPagarParcela, Long> {

    List<ContaPagarParcela> findByContaPagarId(Long contaPagarId);

    @Query("SELECT p FROM ContaPagarParcela p WHERE p.dataVencimento <= :data AND p.paga = false")
    List<ContaPagarParcela> findParcelasVencidas(@Param("data") LocalDate data);

    @Query("SELECT p FROM ContaPagarParcela p WHERE p.dataVencimento BETWEEN :inicio AND :fim")
    List<ContaPagarParcela> findByVencimentoEntre(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
