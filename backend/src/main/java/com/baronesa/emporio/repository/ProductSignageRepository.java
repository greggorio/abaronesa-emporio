package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ProductSignage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSignageRepository extends JpaRepository<ProductSignage, Long> {
    Optional<ProductSignage> findByProdutoId(Long produtoId);
    
    @Query("""
            SELECT ps FROM ProductSignage ps
            JOIN FETCH ps.produto p
            WHERE ps.enabled = true
              AND (ps.metadataSource = 'AUTO_AI')
              AND p.nome IS NOT NULL AND p.descricao IS NOT NULL AND p.imagemPrincipal IS NOT NULL
            """)
    List<ProductSignage> findEligibleForJob();
    
    /**
     * Busca produtos habilitados com vídeo gerado (mp4Url preenchido)
     * para sincronização com signage-api
     */
    @Query("""
            SELECT ps FROM ProductSignage ps
            JOIN FETCH ps.produto p
            WHERE ps.enabled = true
              AND ps.mp4Url IS NOT NULL
              AND ps.status = 'rendered'
            """)
    List<ProductSignage> findEnabledWithVideo();
    
    /**
     * Busca produto por renderHash
     */
    Optional<ProductSignage> findByRenderHash(String renderHash);
}
