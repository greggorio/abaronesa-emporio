package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Permissoes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PermissoesRepositorio  extends JpaRepository<Permissoes, Long> {

    Optional<Permissoes> findByPermissao(String permissao);

    @Query("SELECT DISTINCT p FROM Permissoes p ORDER BY p.descricao")
    List<Permissoes> findAllDistinct();

}
