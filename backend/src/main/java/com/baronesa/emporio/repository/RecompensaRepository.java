package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Recompensa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecompensaRepository extends JpaRepository<Recompensa, Long> {

    @Query("SELECT COUNT(r) FROM Recompensa r WHERE r.ativo = true")
    int countAtivas();

    @Query("SELECT r FROM Recompensa r WHERE r.ativo = true " +
           "AND (r.validadeInicio IS NULL OR r.validadeInicio <= :hoje) " +
           "AND (r.validadeFim IS NULL OR r.validadeFim >= :hoje) " +
           "AND (r.estoque IS NULL OR r.estoque > 0) " +
           "ORDER BY r.pontosNecessarios ASC, r.nome ASC")
    List<Recompensa> findDisponiveis(@Param("hoje") LocalDate hoje);

    default List<Recompensa> findDisponiveis() {
        return findDisponiveis(LocalDate.now());
    }
}
