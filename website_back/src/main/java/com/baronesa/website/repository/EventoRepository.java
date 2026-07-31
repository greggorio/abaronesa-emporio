package com.baronesa.website.repository;

import com.baronesa.website.entity.Evento;
import com.baronesa.website.enums.EventoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

    /**
     * Busca eventos futuros ativos ordenados por data
     * @param dataAtual data/hora atual
     * @param pageable paginação (para limitar a 4 na home)
     * @return lista de eventos futuros
     */
    @Query("SELECT e FROM Evento e WHERE e.ativo = true AND e.status = 'AGENDADO' AND FUNCTION('date', e.dataEvento) >= FUNCTION('date', :dataAtual) ORDER BY e.dataEvento ASC")
    List<Evento> findEventosFuturos(LocalDateTime dataAtual, Pageable pageable);

    /**
     * Busca eventos realizados ordenados por data decrescente
     * @param status status REALIZADO
     * @return lista de eventos realizados
     */
    List<Evento> findByAtivoTrueAndStatusOrderByDataEventoDesc(EventoStatus status);

    /**
     * Busca todos eventos ativos por status
     * @param ativo se está ativo
     * @param status status do evento
     * @return lista de eventos
     */
    List<Evento> findByAtivoAndStatusOrderByDataEventoDesc(Boolean ativo, EventoStatus status);

    /**
     * Busca todos eventos com paginação (para admin)
     * @param pageable paginação
     * @return página de eventos
     */
    Page<Evento> findAllByOrderByDataEventoDesc(Pageable pageable);

    /**
     * Busca eventos com filtro por status e paginação (para admin)
     * @param status status do evento
     * @param pageable paginação
     * @return página de eventos
     */
    Page<Evento> findByStatusOrderByDataEventoDesc(EventoStatus status, Pageable pageable);

    /**
     * Busca eventos com pesquisa de texto em título, banda ou gênero (para admin)
     * @param searchTerm termo de busca
     * @param pageable paginação
     * @return página de eventos
     */
    @Query("SELECT e FROM Evento e WHERE " +
            "LOWER(e.titulo) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.banda) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.genero) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY e.dataEvento DESC")
    Page<Evento> searchEventos(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Busca eventos com pesquisa de texto e filtro por status (para admin)
     * @param searchTerm termo de busca
     * @param status status do evento
     * @param pageable paginação
     * @return página de eventos
     */
    @Query("SELECT e FROM Evento e WHERE " +
            "e.status = :status AND (" +
            "LOWER(e.titulo) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.banda) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.genero) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY e.dataEvento DESC")
    Page<Evento> searchEventosByStatus(
            @Param("searchTerm") String searchTerm,
            @Param("status") EventoStatus status,
            Pageable pageable
    );

    /**
     * Busca eventos que ocorram dentro do intervalo informado para o dashboard.
     */
    @Query("SELECT e FROM Evento e WHERE e.ativo = true AND e.dataEvento <= :fim AND " +
            "(e.dataHoraFim IS NULL OR e.dataHoraFim >= :inicio) ORDER BY e.dataEvento ASC")
    List<Evento> findEventosParaDashboard(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    /**
     * Verifica se existe evento ativo que sobrepõe o intervalo informado.
     * Sobreposição: inicio <= fimExistente && fimNovo >= inicioExistente.
     */
    @Query("SELECT COUNT(e) > 0 FROM Evento e " +
            "WHERE e.ativo = true " +
            "AND (:ignoreId IS NULL OR e.id <> :ignoreId) " +
            "AND e.dataEvento <= :fimNovo " +
            "AND COALESCE(e.dataHoraFim, e.dataEvento) >= :inicioNovo")
    boolean existsEventoSobreposto(
            @Param("inicioNovo") LocalDateTime inicioNovo,
            @Param("fimNovo") LocalDateTime fimNovo,
            @Param("ignoreId") Long ignoreId
    );
}
