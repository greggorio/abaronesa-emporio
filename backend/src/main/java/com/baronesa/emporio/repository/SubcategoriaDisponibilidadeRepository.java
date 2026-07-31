package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.SubcategoriaDisponibilidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubcategoriaDisponibilidadeRepository extends JpaRepository<SubcategoriaDisponibilidade, Long> {
    List<SubcategoriaDisponibilidade> findBySubcategoriaId(Long subcategoriaId);
    List<SubcategoriaDisponibilidade> findBySubcategoriaIdAndAtivoTrue(Long subcategoriaId);
}
