package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByTelefone(String telefone);

    boolean existsByEmail(String email);

    Optional<Usuario> findByEmailVerificationToken(String token);

    Optional<Usuario> findByPasswordResetToken(String token);

    List<Usuario> findByAtivoTrue();

    @Query("SELECT DISTINCT u FROM Usuario u WHERE :role MEMBER OF u.roles")
    List<Usuario> findByRolesContaining(@Param("role") Usuario.Role role);

    @Query("SELECT DISTINCT u FROM Usuario u WHERE u.ativo = true AND :excludedRole NOT MEMBER OF u.roles")
    List<Usuario> findByAtivoTrueAndRolesNotContaining(@Param("excludedRole") Usuario.Role excludedRole);

    @Query("SELECT u FROM Usuario u LEFT JOIN u.perfilCliente pc WHERE u.ativo = true AND pc.dataNascimento IS NOT NULL AND MONTH(pc.dataNascimento) = :month AND DAY(pc.dataNascimento) = :day AND 'CLIENTE' MEMBER OF u.roles")
    List<Usuario> findClientesByBirthdayMonthDay(@Param("month") int month, @Param("day") int day);
}
