package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.CategoriaDespesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategoriaDespesaRepository extends JpaRepository<CategoriaDespesa, Long>, JpaSpecificationExecutor<CategoriaDespesa> {
}
