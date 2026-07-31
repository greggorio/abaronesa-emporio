package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.TarefaValidade;
import com.baronesa.emporio.entity.TarefaValidadeDivergencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TarefaValidadeDivergenciaRepository extends JpaRepository<TarefaValidadeDivergencia, Long> {
    List<TarefaValidadeDivergencia> findByTarefa(TarefaValidade tarefa);
    List<TarefaValidadeDivergencia> findByTarefaId(Long tarefaId);
}
