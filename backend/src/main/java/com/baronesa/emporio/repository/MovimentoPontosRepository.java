package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.MovimentoPontos;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.TipoMovimentoPontos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovimentoPontosRepository extends JpaRepository<MovimentoPontos, Long> {

    Optional<MovimentoPontos> findFirstByClienteOrderByDataHoraDesc(Usuario cliente);

    boolean existsByReferenciaTipoAndReferenciaId(String referenciaTipo, Long referenciaId);

    List<MovimentoPontos> findTop50ByClienteOrderByDataHoraDesc(Usuario cliente);
    
    @Query(value = "SELECT COUNT(DISTINCT cliente_id) FROM (" +
           "SELECT mp.cliente_id, MIN(mp.data_hora) as primeira_data FROM movimentos_pontos mp " +
           "JOIN usuario_roles ur ON mp.cliente_id = ur.usuario_id AND ur.role = 'CLIENTE' " +
           "WHERE mp.tipo = 'GANHO' GROUP BY mp.cliente_id HAVING MIN(mp.data_hora) >= :dataInicio" +
           ") AS subquery", nativeQuery = true)
    int countAdesoesDesde(@Param("dataInicio") LocalDateTime dataInicio);

    @Query("SELECT COUNT(DISTINCT mp.cliente.id) FROM MovimentoPontos mp JOIN mp.cliente.roles r WHERE r = 'CLIENTE' AND mp.tipo = :tipoMovimento")
    int countParticipantesComPontos(@Param("tipoMovimento") TipoMovimentoPontos tipoMovimento);

    @Query("SELECT SUM(mp.pontos) FROM MovimentoPontos mp WHERE mp.tipo = :tipoMovimento AND mp.dataHora >= :dataInicio AND mp.origem = 'VENDA'")
    Integer sumPontosEmitidosDesde(@Param("tipoMovimento") TipoMovimentoPontos tipoMovimento,
                                  @Param("dataInicio") LocalDateTime dataInicio);
    
    // Query para obter top pontuadores nos últimos 30 dias
    @Query(value = "SELECT u.id, u.nome, SUM(mp.pontos) as total_pontos FROM movimentos_pontos mp " +
           "JOIN usuarios u ON mp.cliente_id = u.id " +
           "JOIN usuario_roles ur ON u.id = ur.usuario_id AND ur.role = 'CLIENTE' " +
           "WHERE mp.tipo = 'GANHO' AND mp.origem = 'VENDA' AND mp.data_hora >= :dataInicio " +
           "GROUP BY u.id, u.nome ORDER BY total_pontos DESC LIMIT 10", nativeQuery = true)
    List<Object[]> findTopPontuadoresDesdeRaw(@Param("dataInicio") LocalDateTime dataInicio);

    // Query para obter top saldos atuais
    @Query(value = "SELECT u.id, u.nome, mp.saldo_apos " +
           "FROM movimentos_pontos mp " +
           "JOIN usuarios u ON mp.cliente_id = u.id " +
           "JOIN usuario_roles ur ON u.id = ur.usuario_id AND ur.role = 'CLIENTE' " +
           "WHERE mp.data_hora = (" +
           "  SELECT MAX(mp2.data_hora) FROM movimentos_pontos mp2 " +
           "  WHERE mp2.cliente_id = mp.cliente_id" +
           ") " +
           "ORDER BY mp.saldo_apos DESC LIMIT 10", nativeQuery = true)
    List<Object[]> findTopSaldosRaw();

    @Query(value = """
        SELECT DISTINCT ON (mp.cliente_id) mp.cliente_id, mp.saldo_apos
        FROM movimentos_pontos mp
        JOIN usuarios u ON u.id = mp.cliente_id
        JOIN usuario_roles ur ON ur.usuario_id = u.id AND ur.role = 'CLIENTE'
        WHERE mp.cliente_id IN (:clienteIds)
        ORDER BY mp.cliente_id, mp.data_hora DESC
        """, nativeQuery = true)
    List<Object[]> findLatestSaldoByClientes(@Param("clienteIds") List<Long> clienteIds);
    
    // Query para obter soma de pontos resgatados nos últimos 30 dias
    @Query(value = "SELECT ABS(SUM(mp.pontos)) FROM movimentos_pontos mp " +
           "WHERE mp.tipo = 'RESGATE' AND mp.data_hora >= :dataInicio", nativeQuery = true)
    Integer sumPontosResgatadosDesde(@Param("dataInicio") LocalDateTime dataInicio);
    
    // Query para obter últimos 10 resgates com informações de cliente e recompensa
    @Query(value = "SELECT u.id, u.nome, r.id, r.nome, mp.pontos, mp.data_hora " +
           "FROM movimentos_pontos mp " +
           "JOIN usuarios u ON mp.cliente_id = u.id " +
           "JOIN usuario_roles ur ON u.id = ur.usuario_id AND ur.role = 'CLIENTE' " +
           "JOIN recompensas r ON mp.referencia_id = r.id " +
           "WHERE mp.tipo = 'RESGATE' " +
           "ORDER BY mp.data_hora DESC " +
           "LIMIT 10", nativeQuery = true)
    List<Object[]> findUltimosResgatesRaw();
    
    // Query para obter top 5 recompensas mais resgatadas nos últimos 30 dias
    @Query(value = "SELECT r.id, r.nome, COUNT(*) as total_resgates, ABS(SUM(mp.pontos)) as pontos_total " +
           "FROM movimentos_pontos mp " +
           "JOIN recompensas r ON mp.referencia_id = r.id " +
           "WHERE mp.tipo = 'RESGATE' AND mp.data_hora >= :dataInicio " +
           "GROUP BY r.id, r.nome " +
           "ORDER BY total_resgates DESC, pontos_total DESC " +
           "LIMIT 5", nativeQuery = true)
    List<Object[]> findTopRecompensasResgatadasUltimos30Dias(@Param("dataInicio") LocalDateTime dataInicio);

    // Query para obter clientes com role 'CLIENTE' que têm movimentos de pontos
    @Query(value = "SELECT DISTINCT u.id, u.nome, u.telefone, u.email " +
           "FROM movimentos_pontos mp " +
           "JOIN usuarios u ON mp.cliente_id = u.id " +
           "JOIN usuario_roles ur ON u.id = ur.usuario_id AND ur.role = 'CLIENTE' " +
           "WHERE mp.saldo_apos IS NOT NULL " +
           "ORDER BY u.nome ASC", nativeQuery = true)
    List<Object[]> findClientesComPontosRaw();
}
