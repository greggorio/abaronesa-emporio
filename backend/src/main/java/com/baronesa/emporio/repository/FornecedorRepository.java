package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Fornecedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FornecedorRepository extends JpaRepository<Fornecedor, Long>, JpaSpecificationExecutor<Fornecedor> {

    Optional<Fornecedor> findByCnpj(String cnpj);

    List<Fornecedor> findByAtivoTrue();

    @Query("SELECT f FROM Fornecedor f WHERE f.ativo = true ORDER BY f.nomeFantasia, f.razaoSocial")
    List<Fornecedor> findAllAtivosOrdenados();

    boolean existsByCnpjAndIdNot(String cnpj, Long id);

    // Buscar fornecedores ativos com paginação
    Page<Fornecedor> findByAtivoTrue(Pageable pageable);

    // Buscar por ID, razão social ou CNPJ
    @Query("SELECT f FROM Fornecedor f WHERE f.ativo = true AND (" +
            "CAST(f.id AS string) LIKE CONCAT('%', :search, '%') OR " +
            "LOWER(f.razaoSocial) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "f.cnpj LIKE CONCAT('%', :search, '%'))")
    List<Fornecedor> buscarPorCodigoRazaoOuCnpj(@Param("search") String search);

    // Método para lookup - busca em múltiplos campos
    @Query("SELECT f FROM Fornecedor f WHERE " +
            "LOWER(f.cnpj) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(f.razaoSocial) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(f.nomeFantasia) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(f.cidade) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(f.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(f.contato) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Fornecedor> searchForLookup(@Param("search") String search);
}