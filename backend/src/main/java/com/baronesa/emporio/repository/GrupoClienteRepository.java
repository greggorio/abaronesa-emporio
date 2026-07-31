package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.GrupoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GrupoClienteRepository
        extends JpaRepository<GrupoCliente, Long>,
        JpaSpecificationExecutor<GrupoCliente> {

    /**
     * Busca grupo por descrição ignorando case
     * @param descricao nome do grupo
     * @return Optional com o grupo encontrado
     */
    Optional<GrupoCliente> findByDescricaoIgnoreCase(String descricao);

    /**
     * Verifica se existe grupo com a descrição ignorando case
     * @param descricao nome do grupo
     * @return true se existe
     */
    boolean existsByDescricaoIgnoreCase(String descricao);

    /**
     * Busca grupo por descrição (case sensitive)
     * @param descricao nome do grupo
     * @return Optional com o grupo encontrado
     */
    Optional<GrupoCliente> findByDescricao(String descricao);

    /**
     * Verifica se existe grupo com a descrição (case sensitive)
     * @param descricao nome do grupo
     * @return true se existe
     */
    boolean existsByDescricao(String descricao);
}