package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.PedidoCompraItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoCompraItemRepository extends JpaRepository<PedidoCompraItem, Long> {
    List<PedidoCompraItem> findByPedidoId(Long pedidoId);
}

