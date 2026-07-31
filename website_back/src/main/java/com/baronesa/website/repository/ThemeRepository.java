package com.baronesa.website.repository;

import com.baronesa.website.entity.Theme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThemeRepository extends JpaRepository<Theme, Long> {
    
    List<Theme> findByTenantId(String tenantId);
    
    List<Theme> findByTenantIdAndStatus(String tenantId, com.baronesa.website.enums.ThemeStatus status);
    
    @Query("SELECT t FROM Theme t WHERE t.tenantId = :tenantId AND (t.baseThemeId IS NULL OR t.baseThemeId = :baseThemeId)")
    List<Theme> findByTenantIdAndBaseThemeId(@Param("tenantId") String tenantId, @Param("baseThemeId") Long baseThemeId);
    
    @Query("SELECT t FROM Theme t WHERE t.tenantId = :tenantId AND t.name = :name")
    Optional<Theme> findByTenantIdAndName(@Param("tenantId") String tenantId, @Param("name") String name);
}