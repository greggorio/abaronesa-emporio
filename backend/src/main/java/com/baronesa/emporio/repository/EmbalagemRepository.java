package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Embalagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmbalagemRepository extends JpaRepository<Embalagem, Long> {
    List<Embalagem> findByProdutoId(Long produtoId);
}

