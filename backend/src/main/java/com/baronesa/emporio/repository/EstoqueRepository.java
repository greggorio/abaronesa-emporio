package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Estoque;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstoqueRepository extends JpaRepository<Estoque, Long> {
}