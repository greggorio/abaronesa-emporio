package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.GrupoClienteDesconto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrupoClienteDescontoRepository extends JpaRepository<GrupoClienteDesconto, Long>,
        JpaSpecificationExecutor<GrupoClienteDesconto> {

    List<GrupoClienteDesconto> findByGrupoClienteIdAndAtivoTrue(Long grupoClienteId);

    Optional<GrupoClienteDesconto> findByGrupoClienteIdAndCategoriaIdAndSubcategoriaIsNullAndAtivoTrue(
            Long grupoClienteId,
            Long categoriaId
    );

    Optional<GrupoClienteDesconto> findByGrupoClienteIdAndCategoriaIdAndSubcategoriaIdAndAtivoTrue(
            Long grupoClienteId,
            Long categoriaId,
            Long subcategoriaId
    );
}
