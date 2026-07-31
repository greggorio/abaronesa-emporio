package com.baronesa.website.repository;

import com.baronesa.website.entity.Foto;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FotoRepository extends JpaRepository<Foto, Long> {

    List<Foto> findByCategoriaIdOrderByCreatedAtDesc(Long categoriaId);

    void deleteByCategoriaId(Long categoriaId);

    boolean existsByCategoriaId(Long categoriaId);

    interface CategoriaFotoCountProjection {
        Long getCategoriaId();
        Long getTotal();
    }

    @Query("select f.categoria.id as categoriaId, count(f) as total from Foto f group by f.categoria.id")
    List<CategoriaFotoCountProjection> countFotosByCategoria();
}
