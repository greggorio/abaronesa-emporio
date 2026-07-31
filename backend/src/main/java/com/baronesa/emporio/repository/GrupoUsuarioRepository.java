package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.GrupoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GrupoUsuarioRepository extends JpaRepository<GrupoUsuario, Long>, JpaSpecificationExecutor<GrupoUsuario> {
}
