package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long>, JpaSpecificationExecutor<Mesa> {

    Optional<Mesa> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
