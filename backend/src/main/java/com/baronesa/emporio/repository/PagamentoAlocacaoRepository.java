package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.entity.PagamentoAlocacao;
import com.baronesa.emporio.entity.SessaoConvidado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PagamentoAlocacaoRepository extends JpaRepository<PagamentoAlocacao, Long> {

    @Query("SELECT COALESCE(SUM(a.valor),0) FROM PagamentoAlocacao a " +
            "JOIN a.pagamento p " +
            "WHERE a.sessaoConvidado = :convidado " +
            "AND p.status = com.baronesa.emporio.enums.StatusPagamento.PAID")
    BigDecimal sumPagoAlocadoPorConvidado(@Param("convidado") SessaoConvidado convidado);

    List<PagamentoAlocacao> findByPagamento(Pagamento pagamento);

    java.util.List<PagamentoAlocacao> findBySessaoConvidado(SessaoConvidado convidado);
}
