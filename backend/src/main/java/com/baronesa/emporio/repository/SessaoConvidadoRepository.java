package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.SessaoConvidado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.baronesa.emporio.enums.StatusSessao;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessaoConvidadoRepository extends JpaRepository<SessaoConvidado, Long> {

    Optional<SessaoConvidado> findByGuestToken(String token);

    Optional<SessaoConvidado> findFirstByUsuario_IdAndSessaoMesa_Status(Long usuarioId, StatusSessao status);

    long countByUsuario_IdAndEntrouEmAfter(Long usuarioId, java.time.LocalDateTime data);

    // Navega pela associação: sessaoMesa.id
    long countBySessaoMesa_Id(Long sessaoMesaId);

    List<SessaoConvidado> findBySessaoMesa_Id(Long sessaoMesaId);
}
