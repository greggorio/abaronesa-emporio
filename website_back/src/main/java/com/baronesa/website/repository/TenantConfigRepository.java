package com.baronesa.website.repository;

import com.baronesa.website.entity.TenantConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, String> {
}
