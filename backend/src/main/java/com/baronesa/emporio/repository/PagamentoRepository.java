package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.enums.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long>, JpaSpecificationExecutor<Pagamento> {
    List<Pagamento> findBySessaoMesaAndStatus(SessaoMesa sessaoMesa, StatusPagamento status);
    List<Pagamento> findBySessaoConvidadoAndStatus(SessaoConvidado convidado, StatusPagamento status);
    List<Pagamento> findBySessaoMesa(SessaoMesa sessaoMesa);
    List<Pagamento> findByProviderRef(String providerRef);
    boolean existsBySessaoMesaAndStatus(SessaoMesa sessaoMesa, StatusPagamento status);
    List<Pagamento> findByPagoEmBetween(LocalDateTime inicio, LocalDateTime fim);

    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM Pagamento p " +
           "WHERE p.status = com.baronesa.emporio.enums.StatusPagamento.PAID " +
           "AND p.pagoEm >= :startDate AND p.pagoEm < :endDate")
    BigDecimal sumValorPagoByDate(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(p) FROM Pagamento p " +
           "WHERE p.status = com.baronesa.emporio.enums.StatusPagamento.PAID " +
           "AND p.pagoEm >= :startDate AND p.pagoEm < :endDate")
    Long countVendasByDate(@Param("startDate") LocalDateTime startDate,
                           @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT metodo, cartao_tipo, SUM(valor) as total " +
                   "FROM pagamento " +
                   "WHERE status = 'PAID' " +
                   "AND pago_em >= :startDate AND pago_em < :endDate " +
                   "GROUP BY metodo, cartao_tipo", nativeQuery = true)
    List<Object[]> findValoresPorMetodoByDate(@Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(p.valorBase), 0) FROM Pagamento p " +
           "WHERE p.status = com.baronesa.emporio.enums.StatusPagamento.PAID " +
           "AND p.pagoEm >= :startDate AND p.pagoEm < :endDate")
    BigDecimal sumValorBaseByDate(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(p.valorTaxaServico), 0) FROM Pagamento p " +
           "WHERE p.status = com.baronesa.emporio.enums.StatusPagamento.PAID " +
           "AND p.pagoEm >= :startDate AND p.pagoEm < :endDate")
    BigDecimal sumValorTaxaServicoByDate(@Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);

    @Query("""
            SELECT u.id,
                   u.nome,
                   COALESCE(SUM(p.valor), 0) as totalConsumido,
                   pf.voucherVr
            FROM Pagamento p
            JOIN p.sessaoConvidado sc
            JOIN sc.usuario u
            LEFT JOIN PerfilFuncionario pf ON pf.usuario = u
            WHERE p.status = com.baronesa.emporio.enums.StatusPagamento.PAID
              AND LOWER(p.metodo) = 'voucher'
              AND p.pagoEm >= :inicio AND p.pagoEm < :fim
              AND (
                   com.baronesa.emporio.entity.Usuario$Role.ADMIN MEMBER OF u.roles
                OR com.baronesa.emporio.entity.Usuario$Role.FUNCIONARIO MEMBER OF u.roles
                OR com.baronesa.emporio.entity.Usuario$Role.WAITER MEMBER OF u.roles
                OR com.baronesa.emporio.entity.Usuario$Role.KDS MEMBER OF u.roles
                OR com.baronesa.emporio.entity.Usuario$Role.CAIXA MEMBER OF u.roles
              )
            GROUP BY u.id, u.nome, pf.voucherVr
            """)
    List<Object[]> findConsumoVoucherFuncionarios(@Param("inicio") LocalDateTime inicio,
                                                  @Param("fim") LocalDateTime fim);

    @Query("SELECT p FROM Pagamento p WHERE p.pagoEm BETWEEN :inicio AND :fim AND p.valorCouvert IS NOT NULL")
    List<Pagamento> findByPagoEmBetweenAndValorCouvertIsNotNull(@Param("inicio") LocalDateTime inicio,
                                                               @Param("fim") LocalDateTime fim);

    @Query(value = """
            SELECT DATE(pago_em) AS dia,
                   COUNT(*) AS quantidade,
                   COALESCE(SUM(valor), 0) AS total
            FROM pagamento
            WHERE status = 'PAID'
              AND pago_em >= :inicio
              AND pago_em < :fim
            GROUP BY DATE(pago_em)
            ORDER BY dia ASC
            """, nativeQuery = true)
    List<Object[]> findVendasAgrupadasPorDia(@Param("inicio") LocalDateTime inicio,
                                             @Param("fim") LocalDateTime fim);

    java.util.Optional<Pagamento> findFirstByProviderRef(String providerRef);
    java.util.Optional<Pagamento> findFirstBySessaoMesaAndStatusOrderByIdDesc(SessaoMesa sessaoMesa, StatusPagamento status);
    java.util.Optional<Pagamento> findFirstBySessaoConvidadoAndStatusOrderByIdDesc(SessaoConvidado convidado, StatusPagamento status);

    List<Pagamento> findTop50BySessaoMesaIsNotNullAndStatusInOrderByCriadoEmDesc(List<StatusPagamento> status);
    List<Pagamento> findTop50BySessaoMesaIsNotNullAndSelfCheckoutOrigemAndSelfCheckoutResolvidoAndStatusInOrderByCriadoEmDesc(
            String origem,
            Boolean resolvido,
            List<StatusPagamento> status
    );
}
