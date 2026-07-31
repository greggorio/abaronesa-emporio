package com.baronesa.website.repository;

import com.baronesa.website.entity.CategoriaFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaFotoRepository extends JpaRepository<CategoriaFoto, Long> {

    List<CategoriaFoto> findAllByOrderByOrdemAsc();

    List<CategoriaFoto> findAllByAtivoTrueOrderByOrdemAsc();
}
