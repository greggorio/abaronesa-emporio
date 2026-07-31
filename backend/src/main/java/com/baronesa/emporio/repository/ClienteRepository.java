package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    @Query("SELECT u FROM Usuario u WHERE :role MEMBER OF u.roles")
    Page<Usuario> findByRole(@Param("role") Usuario.Role role, Pageable pageable);

    @Query("SELECT u FROM Usuario u WHERE :role MEMBER OF u.roles")
    List<Usuario> findByRole(@Param("role") Usuario.Role role);

    @Query("SELECT u FROM Usuario u WHERE u.id = :id AND :role MEMBER OF u.roles")
    Optional<Usuario> findByIdAndRole(@Param("id") Long id, @Param("role") Usuario.Role role);

    @Query("SELECT u FROM Usuario u WHERE u.email = :email AND :role MEMBER OF u.roles")
    Optional<Usuario> findByEmailAndRole(@Param("email") String email, @Param("role") Usuario.Role role);

    @Query("SELECT u FROM Usuario u LEFT JOIN FETCH u.perfilCliente WHERE u.id = :id AND :role MEMBER OF u.roles")
    Optional<Usuario> findByIdWithPerfilCliente(@Param("id") Long id, @Param("role") Usuario.Role role);

    @Query("SELECT COUNT(u) > 0 FROM Usuario u WHERE u.email = :email AND :role MEMBER OF u.roles")
    boolean existsByEmailAndRole(@Param("email") String email, @Param("role") Usuario.Role role);

    /**
     * Busca cliente por CPF
     */
    @Query("""
        SELECT u FROM Usuario u 
        JOIN u.perfilCliente pc 
        WHERE pc.cpf = :cpf 
        AND u.roles LIKE '%CLIENTE%'
        """)
    Optional<Usuario> findByCpf(@Param("cpf") String cpf);

    /**
     * Busca cliente por email ou CPF
     */
    @Query("""
        SELECT u FROM Usuario u 
        LEFT JOIN u.perfilCliente pc 
        WHERE (u.email = :email OR pc.cpf = :cpf)
        AND u.roles LIKE '%CLIENTE%'
        """)
    Optional<Usuario> findByEmailOrCpf(@Param("email") String email, @Param("cpf") String cpf);

    @Query("SELECT COUNT(u) FROM Usuario u WHERE :role MEMBER OF u.roles")
    long countByRole(@Param("role") Usuario.Role role);

    @Query("SELECT COUNT(u) FROM Usuario u WHERE :role MEMBER OF u.roles AND u.criadoEm >= :createdAfter")
    long countByRoleAndCreatedAfter(@Param("role") Usuario.Role role, @Param("createdAfter") LocalDateTime createdAfter);
}
