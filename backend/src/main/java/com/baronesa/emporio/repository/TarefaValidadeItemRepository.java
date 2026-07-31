package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.TarefaValidade;
import com.baronesa.emporio.entity.TarefaValidadeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TarefaValidadeItemRepository extends JpaRepository<TarefaValidadeItem, Long> {
    List<TarefaValidadeItem> findByTarefa(TarefaValidade tarefa);
}
