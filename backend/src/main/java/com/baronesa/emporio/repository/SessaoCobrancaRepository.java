package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.SessaoCobranca;
import com.baronesa.emporio.enums.StatusCobranca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessaoCobrancaRepository extends JpaRepository<SessaoCobranca, Long> {

    List<SessaoCobranca> findBySessaoMesaIdAndStatus(Long sessaoMesaId, StatusCobranca status);

    List<SessaoCobranca> findBySessaoConvidadoIdAndStatus(Long sessaoConvidadoId, StatusCobranca status);

    @Query("SELECT SUM(sc.valor) FROM SessaoCobranca sc WHERE sc.sessaoMesaId = :sessaoMesaId AND sc.status = :status AND sc.isento = false")
    Optional<Double> sumValorBySessaoMesaIdAndStatusAndNotIsento(Long sessaoMesaId, StatusCobranca status);
}