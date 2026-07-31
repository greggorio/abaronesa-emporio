package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Subcategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SubcategoriaRepository
        extends JpaRepository<Subcategoria, Long>,
        JpaSpecificationExecutor<Subcategoria> {   // ← adicionamos isso

    List<Subcategoria> findByCategoriaId(Long categoriaId);
}
