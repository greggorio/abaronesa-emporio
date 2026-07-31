package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.MovimentoEstoqueLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MovimentoEstoqueLoteRepository extends JpaRepository<MovimentoEstoqueLote, Long> {
    @Query("""
        SELECT mel FROM MovimentoEstoqueLote mel
        JOIN FETCH mel.movimentoEstoque me
        LEFT JOIN FETCH me.usuario u
        WHERE mel.estoqueLote.id = :estoqueLoteId
        ORDER BY COALESCE(me.dataMovimento, mel.createdAt) DESC, mel.id DESC
    """)
    List<MovimentoEstoqueLote> findByEstoqueLoteIdOrderByMovimentoDesc(Long estoqueLoteId);
}
