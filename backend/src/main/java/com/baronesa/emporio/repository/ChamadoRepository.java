package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Chamado;
import com.baronesa.emporio.enums.StatusChamado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChamadoRepository extends JpaRepository<Chamado, Long> {

    List<Chamado> findByStatusOrderByCriadoEmAsc(StatusChamado status);

    long countByStatus(StatusChamado status);
}
