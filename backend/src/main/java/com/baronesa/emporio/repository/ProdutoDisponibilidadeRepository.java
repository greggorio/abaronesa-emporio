package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ProdutoDisponibilidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ProdutoDisponibilidadeRepository extends JpaRepository<ProdutoDisponibilidade, Long> {
    List<ProdutoDisponibilidade> findByProdutoId(Long produtoId);
    List<ProdutoDisponibilidade> findByProdutoIdAndAtivoTrue(Long produtoId);
    List<ProdutoDisponibilidade> findByProdutoIdIn(Set<Long> produtoIds);
}
