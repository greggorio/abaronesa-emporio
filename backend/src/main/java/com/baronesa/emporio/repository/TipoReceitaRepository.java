package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.TipoReceita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface TipoReceitaRepository extends JpaRepository<TipoReceita, Long>, JpaSpecificationExecutor<TipoReceita> {
    Optional<TipoReceita> findByNomeIgnoreCase(String nome);
    boolean existsByNomeIgnoreCase(String nome);
}
