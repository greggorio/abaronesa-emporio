package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.FichaTecnicaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FichaTecnicaItemRepository extends JpaRepository<FichaTecnicaItem, Long> {

    List<FichaTecnicaItem> findByFichaTecnicaIdOrderByOrdemAsc(Long fichaTecnicaId);

    void deleteByFichaTecnicaId(Long fichaTecnicaId);
}
