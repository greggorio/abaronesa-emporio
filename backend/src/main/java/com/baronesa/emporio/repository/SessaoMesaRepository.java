package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Mesa;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.enums.StatusSessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessaoMesaRepository extends JpaRepository<SessaoMesa, Long> {

    Optional<SessaoMesa> findFirstByMesaAndStatusOrderByAbertaEmDesc(Mesa mesa, StatusSessao status);

    Long countByStatus(StatusSessao status);
}
