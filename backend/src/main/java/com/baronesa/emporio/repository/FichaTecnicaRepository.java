package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.FichaTecnica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FichaTecnicaRepository extends JpaRepository<FichaTecnica, Long> {

    Optional<FichaTecnica> findByProdutoId(Long produtoId);

    @Query("SELECT f FROM FichaTecnica f " +
           "LEFT JOIN FETCH f.itens i " +
           "LEFT JOIN FETCH i.insumoSku s " +
           "LEFT JOIN FETCH s.produto p " +
           "LEFT JOIN FETCH s.embalagem " +
           "WHERE f.produto.id = :produtoId")
    Optional<FichaTecnica> findByProdutoIdWithItens(@Param("produtoId") Long produtoId);

    boolean existsByProdutoId(Long produtoId);
}
