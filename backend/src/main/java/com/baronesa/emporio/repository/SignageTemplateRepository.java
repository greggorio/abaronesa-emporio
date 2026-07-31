package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.SignageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SignageTemplateRepository extends JpaRepository<SignageTemplate, Integer> {
    
    /**
     * Busca template pelo ID único (template_id)
     */
    Optional<SignageTemplate> findByTemplateId(String templateId);
    
    /**
     * Lista todos os templates ativos
     */
    List<SignageTemplate> findAllByIsActiveTrue();
    
    /**
     * Lista templates com IA habilitada
     */
    List<SignageTemplate> findAllByAiEnabledTrueAndIsActiveTrue();
}