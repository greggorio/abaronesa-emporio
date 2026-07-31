package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.PerfilFuncionario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PerfilFuncionarioRepository extends JpaRepository<PerfilFuncionario, Long> {
    
    @Query("SELECT pf FROM PerfilFuncionario pf WHERE pf.usuario.id = :usuarioId")
    Optional<PerfilFuncionario> findByUsuarioId(@Param("usuarioId") Long usuarioId);
}