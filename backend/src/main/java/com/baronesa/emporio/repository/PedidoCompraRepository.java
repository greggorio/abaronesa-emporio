package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.PedidoCompra;
import com.baronesa.emporio.enums.StatusPedidoCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoCompraRepository extends JpaRepository<PedidoCompra, Long>, JpaSpecificationExecutor<PedidoCompra> {

    List<PedidoCompra> findByStatus(StatusPedidoCompra status);

    @Query("select p from PedidoCompra p left join fetch p.itens where p.id = :id")
    PedidoCompra findByIdWithItens(@Param("id") Long id);
}

