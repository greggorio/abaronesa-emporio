package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.PermissoesGrupos;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PermissoesGruposRepositorio extends JpaRepository<PermissoesGrupos, Long> {
    Optional<PermissoesGrupos> findByIdGrupoAndPermissao(Long idGrupo, String permissao);
    List<PermissoesGrupos> findByIdGrupo(Long idGrupo);
    boolean existsByIdGrupoAndPermissao(Long idGrupo, String permissao);

    @Transactional
    void deleteByIdGrupoAndPermissao(Long idGrupo, String permissao);
}
