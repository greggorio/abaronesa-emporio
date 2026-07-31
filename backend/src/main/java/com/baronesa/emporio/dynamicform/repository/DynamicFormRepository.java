package com.baronesa.emporio.dynamicform.repository;

import com.baronesa.emporio.dynamicform.entity.DynamicFormDefinition;
import com.baronesa.emporio.dynamicform.entity.FormComplexityLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DynamicFormRepository extends JpaRepository<DynamicFormDefinition, String> {

    Optional<DynamicFormDefinition> findByEntityTypeAndActiveTrue(String entityType);

    Optional<DynamicFormDefinition> findByEntityType(String entityType);

    List<DynamicFormDefinition> findByComplexityAndActiveTrue(FormComplexityLevel complexity);

    List<DynamicFormDefinition> findByActiveTrue();

    @Query("SELECT d.entityType FROM DynamicFormDefinition d WHERE d.active = true")
    List<String> findAllActiveEntityTypes();

    boolean existsByEntityTypeAndActiveTrue(String entityType);
}