package com.baronesa.website.repository;

import com.baronesa.website.entity.ThemeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ThemeAssignmentRepository extends JpaRepository<ThemeAssignment, Long> {
    
    Optional<ThemeAssignment> findByTenantIdAndIsActiveTrue(String tenantId);
    
    List<ThemeAssignment> findByTenantId(String tenantId);
    
    @Query("SELECT ta FROM ThemeAssignment ta WHERE ta.tenantId = :tenantId AND ta.isActive = true " +
           "AND (ta.validFrom IS NULL OR ta.validFrom <= :now) " +
           "AND (ta.validTo IS NULL OR ta.validTo >= :now) " +
           "ORDER BY ta.priority DESC")
    List<ThemeAssignment> findActiveThemeAssignments(@Param("tenantId") String tenantId, @Param("now") LocalDateTime now);
    
    @Query("SELECT ta FROM ThemeAssignment ta WHERE ta.tenantId = :tenantId AND ta.themeId = :themeId")
    List<ThemeAssignment> findByTenantIdAndThemeId(@Param("tenantId") String tenantId, @Param("themeId") Long themeId);

    @Modifying
    @Query("UPDATE ThemeAssignment ta SET ta.isActive = false WHERE ta.tenantId = :tenantId")
    void deactivateAssignmentsForTenant(@Param("tenantId") String tenantId);
}
