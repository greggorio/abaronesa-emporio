package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UsuarioAdminRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    // Buscar apenas ADMIN ou FUNCIONARIO (ou KDS/WAITER/CAIXA) usando parâmetros
    @Query("SELECT DISTINCT u FROM Usuario u " +
            "LEFT JOIN FETCH u.grupoUsuario " +
            "WHERE :adminRole MEMBER OF u.roles OR :funcionarioRole MEMBER OF u.roles OR :kdsRole MEMBER OF u.roles " +
            "OR :waiterRole MEMBER OF u.roles OR :caixaRole MEMBER OF u.roles")
    Page<Usuario> findAllAdminAndFuncionario(@Param("adminRole") Usuario.Role adminRole,
                                             @Param("funcionarioRole") Usuario.Role funcionarioRole,
                                             @Param("kdsRole") Usuario.Role kdsRole,
                                             @Param("waiterRole") Usuario.Role waiterRole,
                                             @Param("caixaRole") Usuario.Role caixaRole,
                                             Pageable pageable);

    // Sobrecarga sem parâmetros
    default Page<Usuario> findAllAdminAndFuncionario(Pageable pageable) {
        return findAllAdminAndFuncionario(
                Usuario.Role.ADMIN,
                Usuario.Role.FUNCIONARIO,
                Usuario.Role.KDS,
                Usuario.Role.WAITER,
                Usuario.Role.CAIXA,
                pageable
        );
    }

    // Buscar apenas ADMIN
    @Query("SELECT DISTINCT u FROM Usuario u " +
            "LEFT JOIN FETCH u.grupoUsuario " +
            "WHERE :adminRole MEMBER OF u.roles")
    Page<Usuario> findAllAdmin(@Param("adminRole") Usuario.Role adminRole, Pageable pageable);

    // Sobrecarga sem parâmetros
    default Page<Usuario> findAllAdmin(Pageable pageable) {
        return findAllAdmin(Usuario.Role.ADMIN, pageable);
    }

    // Buscar por ID - ADMIN ou FUNCIONARIO
    @Query("SELECT DISTINCT u FROM Usuario u " +
            "LEFT JOIN FETCH u.grupoUsuario " +
            "WHERE u.id = :id " +
            "AND (:adminRole MEMBER OF u.roles OR :funcionarioRole MEMBER OF u.roles " +
            "OR :kdsRole MEMBER OF u.roles OR :waiterRole MEMBER OF u.roles OR :caixaRole MEMBER OF u.roles)")
    Optional<Usuario> findByIdAdminOrFuncionario(@Param("id") Long id,
                                                 @Param("adminRole") Usuario.Role adminRole,
                                                 @Param("funcionarioRole") Usuario.Role funcionarioRole,
                                                 @Param("kdsRole") Usuario.Role kdsRole,
                                                 @Param("waiterRole") Usuario.Role waiterRole,
                                                 @Param("caixaRole") Usuario.Role caixaRole);

    // Sobrecarga sem parâmetros de role
    default Optional<Usuario> findByIdAdminOrFuncionario(Long id) {
        return findByIdAdminOrFuncionario(
                id,
                Usuario.Role.ADMIN,
                Usuario.Role.FUNCIONARIO,
                Usuario.Role.KDS,
                Usuario.Role.WAITER,
                Usuario.Role.CAIXA
        );
    }
}
