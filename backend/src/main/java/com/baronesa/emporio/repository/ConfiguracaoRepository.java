package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Configuracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracaoRepository extends JpaRepository<Configuracao, Long> {

    boolean existsByChave(String chave);

    @Query("SELECT c.valor FROM Configuracao c WHERE c.chave = :chave")
    String findValorByChave(@Param("chave") String chave);

    Optional<Configuracao> findByChave(String chave);
}
