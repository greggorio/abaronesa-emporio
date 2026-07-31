package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Fornecedor;
import com.baronesa.emporio.entity.RecebimentoMercadoria;
import com.baronesa.emporio.entity.StatusRecebimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecebimentoMercadoriaRepository extends JpaRepository<RecebimentoMercadoria, Long>,
        JpaSpecificationExecutor<RecebimentoMercadoria> {

    // Buscar por número da NF
    Optional<RecebimentoMercadoria> findByNumeroNf(String numeroNf);

    // Buscar por chave NFe
    Optional<RecebimentoMercadoria> findByChaveNfe(String chaveNfe);

    // Buscar por fornecedor
    List<RecebimentoMercadoria> findByFornecedorId(Long fornecedorId);

    // Buscar por status
    List<RecebimentoMercadoria> findByStatus(StatusRecebimento status);

    // Buscar por período
    List<RecebimentoMercadoria> findByDataRecebimentoBetween(LocalDateTime inicio, LocalDateTime fim);

    // Verificar se NF já existe para o fornecedor
    boolean existsByNumeroNfAndFornecedorId(String numeroNf, Long fornecedorId);

    // Query customizada para buscar com fornecedor
    @Query("SELECT r FROM RecebimentoMercadoria r " +
            "JOIN FETCH r.fornecedor " +
            "WHERE r.id = :id")
    Optional<RecebimentoMercadoria> findByIdWithFornecedor(@Param("id") Long id);

    // Query para buscar com todos os relacionamentos
    @Query("SELECT DISTINCT r FROM RecebimentoMercadoria r " +
            "LEFT JOIN FETCH r.fornecedor " +
            "LEFT JOIN FETCH r.itens i " +
            "LEFT JOIN FETCH i.produto " +
            "WHERE r.id = :id")
    Optional<RecebimentoMercadoria> findByIdWithAllRelations(@Param("id") Long id);

    // Contar recebimentos por status
    Long countByStatus(StatusRecebimento status);

    // Buscar recebimentos pendentes antigos (para alertas)
    @Query("SELECT r FROM RecebimentoMercadoria r " +
            "WHERE r.status = 'PENDENTE' " +
            "AND r.dataRecebimento < :dataLimite")
    List<RecebimentoMercadoria> findPendentesAntigos(@Param("dataLimite") LocalDateTime dataLimite);


    @Query("""
        SELECT COUNT(r) > 0
        FROM RecebimentoMercadoria r
        WHERE r.numeroNf = :numeroNf AND r.fornecedor.cnpj = :cnpj
    """)
    boolean existsByNumeroNfAndFornecedorCnpj(@Param("numeroNf") String numeroNf, @Param("cnpj") String cnpj);

    boolean existsByFornecedorAndNumeroNf(Fornecedor fornecedor, String numeroNf);


}