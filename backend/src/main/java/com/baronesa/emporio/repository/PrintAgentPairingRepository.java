package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.PrintAgentPairing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrintAgentPairingRepository extends JpaRepository<PrintAgentPairing, Long> {
    Optional<PrintAgentPairing> findByCode(String code);
}
