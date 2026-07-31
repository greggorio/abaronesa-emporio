package com.baronesa.website.repository;

import com.baronesa.website.entity.ClienteRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClienteRefRepository extends JpaRepository<ClienteRef, Long> {

    @Query(value = "SELECT id, nome, email, telefone, cpf, data_nascimento, ativo, erp_updated_at, created_at, updated_at " +
            "FROM clientes_ref " +
            "WHERE LOWER(nome) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(email) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "LIMIT 20", nativeQuery = true)
    List<ClienteRef> searchByNameOrEmailLimited(@Param("query") String query);

    long countByCreatedAtAfter(LocalDateTime createdAfter);
}
