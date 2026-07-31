package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.NfeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NfeRepository extends JpaRepository<NfeModel, Long> {

    Optional<NfeModel> findByChaveAcesso(String chaveAcesso);
    Optional<NfeModel> findByIdVenda(Long idVenda);

    // Métodos para DANFCE/NFCe
    long countByModelo(Integer modelo);
    long countByModeloAndStatus(Integer modelo, String status);
    
    List<NfeModel> findTop5ByModeloOrderByDataEmissaoDesc(Integer modelo);
    
    List<NfeModel> findByModeloOrderByDataEmissaoDesc(Integer modelo);
    List<NfeModel> findByModeloAndStatusOrderByDataEmissaoDesc(Integer modelo, String status);
    
    Page<NfeModel> findByModeloOrderByDataEmissaoDesc(Integer modelo, Pageable pageable);
    Page<NfeModel> findByModeloAndStatusOrderByDataEmissaoDesc(Integer modelo, String status, Pageable pageable);
}
