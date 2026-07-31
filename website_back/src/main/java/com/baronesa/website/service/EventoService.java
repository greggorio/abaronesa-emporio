package com.baronesa.website.service;

import com.baronesa.website.dto.EventoDTO;
import com.baronesa.website.dto.EventoResponseDTO;
import com.baronesa.website.dto.PageResponse;
import com.baronesa.website.entity.Evento;
import com.baronesa.website.enums.EventoStatus;
import com.baronesa.website.repository.EventoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventoService {

    private final EventoRepository eventoRepository;

    /**
     * Lista os próximos 4 eventos futuros para exibir na home page
     */
    public List<EventoResponseDTO> listarProximosEventos() {
        log.info("Buscando próximos 4 eventos futuros");
        List<Evento> eventos = eventoRepository.findEventosFuturos(
                LocalDateTime.now(),
                PageRequest.of(0, 4)
        );
        return eventos.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lista eventos relevantes para o dashboard dentro do período informado.
     */
    public List<EventoResponseDTO> listarEventosParaDashboard(String periodo) {
        log.info("Buscando eventos para dashboard - período {}", periodo);
        LocalDateTime inicio = calcularDataInicio(periodo);
        LocalDateTime fim = LocalDateTime.now();
        List<Evento> eventos = eventoRepository.findEventosParaDashboard(inicio, fim);
        return eventos.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lista eventos realizados para a seção "Shows Realizados"
     */
    public List<EventoResponseDTO> listarEventosRealizados() {
        log.info("Buscando eventos realizados");
        List<Evento> eventos = eventoRepository.findByAtivoTrueAndStatusOrderByDataEventoDesc(EventoStatus.REALIZADO);
        return eventos.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca evento por ID
     */
    public EventoResponseDTO buscarPorId(Long id) {
        log.info("Buscando evento por ID: {}", id);
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com ID: " + id));
        return convertToResponseDTO(evento);
    }

    /**
     * Cria um novo evento
     */
    @Transactional
    public EventoResponseDTO criar(EventoDTO dto) {
        log.info("Criando novo evento: {}", dto.getTitulo());
        validarConflitoHorario(dto.getDataEvento(), dto.getDataHoraFim(), null);
        Evento evento = convertToEntity(dto);
        Evento eventoSalvo = eventoRepository.save(evento);
        log.info("Evento criado com sucesso. ID: {}", eventoSalvo.getId());
        return convertToResponseDTO(eventoSalvo);
    }

    /**
     * Atualiza um evento existente
     */
    @Transactional
    public EventoResponseDTO atualizar(Long id, EventoDTO dto) {
        log.info("Atualizando evento ID: {}", id);
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com ID: " + id));

        validarConflitoHorario(dto.getDataEvento(), dto.getDataHoraFim(), id);
        evento.setTitulo(dto.getTitulo());
        evento.setDescricao(dto.getDescricao());
        evento.setDataEvento(dto.getDataEvento());
        evento.setDataHoraFim(dto.getDataHoraFim());
        evento.setPreco(dto.getPreco());
        evento.setGratuito(dto.getGratuito());
        evento.setBanda(dto.getBanda());
        evento.setGenero(dto.getGenero());
        evento.setImagemUrl(dto.getImagemUrl());
        evento.setAtivo(dto.getAtivo());
        evento.setStatus(dto.getStatus());

        Evento eventoAtualizado = eventoRepository.save(evento);
        log.info("Evento atualizado com sucesso. ID: {}", eventoAtualizado.getId());
        return convertToResponseDTO(eventoAtualizado);
    }

    /**
     * Exclui um evento (soft delete - marca como inativo)
     */
    @Transactional
    public void excluir(Long id) {
        log.info("Excluindo evento ID: {}", id);
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com ID: " + id));
        evento.setAtivo(false);
        eventoRepository.save(evento);
        log.info("Evento marcado como inativo. ID: {}", id);
    }

    /**
     * Lista todos os eventos (para admin) com filtro opcional por status
     */
    public List<EventoResponseDTO> listarTodos(EventoStatus status) {
        log.info("Listando todos os eventos. Status: {}", status);
        List<Evento> eventos;
        if (status != null) {
            eventos = eventoRepository.findByAtivoAndStatusOrderByDataEventoDesc(true, status);
        } else {
            eventos = eventoRepository.findAll();
        }
        return eventos.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lista todos os eventos com paginação e busca (para admin)
     * @param page número da página (0-indexed)
     * @param size tamanho da página
     * @param searchTerm termo de busca (opcional)
     * @param status filtro por status (opcional)
     * @return página de eventos
     */
    public PageResponse<EventoResponseDTO> listarComPaginacao(
            int page,
            int size,
            String searchTerm,
            EventoStatus status
    ) {
        log.info("Listando eventos com paginação - Página: {}, Tamanho: {}, Busca: {}, Status: {}",
                page, size, searchTerm, status);

        Pageable pageable = PageRequest.of(page, size);
        Page<Evento> eventoPage;

        // Determinar qual query usar baseado nos parâmetros
        boolean hasSearch = searchTerm != null && !searchTerm.trim().isEmpty();
        boolean hasStatus = status != null;

        if (hasSearch && hasStatus) {
            eventoPage = eventoRepository.searchEventosByStatus(searchTerm.trim(), status, pageable);
        } else if (hasSearch) {
            eventoPage = eventoRepository.searchEventos(searchTerm.trim(), pageable);
        } else if (hasStatus) {
            eventoPage = eventoRepository.findByStatusOrderByDataEventoDesc(status, pageable);
        } else {
            eventoPage = eventoRepository.findAllByOrderByDataEventoDesc(pageable);
        }

        // Converter Page<Evento> para Page<EventoResponseDTO>
        Page<EventoResponseDTO> dtoPage = eventoPage.map(this::convertToResponseDTO);

        // Criar PageResponse
        return PageResponse.from(dtoPage);
    }

    // Métodos auxiliares de conversão

    private void validarConflitoHorario(LocalDateTime inicio, LocalDateTime fim, Long ignoreId) {
        if (inicio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data/hora de início é obrigatória");
        }

        LocalDateTime fimConsiderado = fim != null ? fim : inicio;
        if (fim != null && fim.isBefore(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data/hora de fim deve ser posterior ao início");
        }

        boolean existeConflito = eventoRepository.existsEventoSobreposto(inicio, fimConsiderado, ignoreId);
        if (existeConflito) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um evento cadastrado nesse intervalo");
        }
    }

    private EventoResponseDTO convertToResponseDTO(Evento evento) {
        EventoResponseDTO dto = new EventoResponseDTO();
        dto.setId(evento.getId());
        dto.setTitulo(evento.getTitulo());
        dto.setDescricao(evento.getDescricao());
        dto.setDataEvento(evento.getDataEvento());
        dto.setDataHoraFim(evento.getDataHoraFim());
        dto.setPreco(evento.getPreco());
        dto.setGratuito(evento.getGratuito());
        dto.setBanda(evento.getBanda());
        dto.setGenero(evento.getGenero());
        dto.setGeneroDescricao(evento.getGenero().getDescricao());
        dto.setImagemUrl(evento.getImagemUrl());
        dto.setAtivo(evento.getAtivo());
        dto.setStatus(evento.getStatus());
        dto.setStatusDescricao(evento.getStatus().getDescricao());
        dto.setCriadoEm(evento.getCriadoEm());
        dto.setAtualizadoEm(evento.getAtualizadoEm());
        return dto;
    }

    private Evento convertToEntity(EventoDTO dto) {
        Evento evento = new Evento();
        evento.setTitulo(dto.getTitulo());
        evento.setDescricao(dto.getDescricao());
        evento.setDataEvento(dto.getDataEvento());
        evento.setDataHoraFim(dto.getDataHoraFim());
        evento.setPreco(dto.getPreco());
        evento.setGratuito(dto.getGratuito() != null ? dto.getGratuito() : false);
        evento.setBanda(dto.getBanda());
        evento.setGenero(dto.getGenero());
        evento.setImagemUrl(dto.getImagemUrl());
        evento.setAtivo(dto.getAtivo() != null ? dto.getAtivo() : true);
        evento.setStatus(dto.getStatus() != null ? dto.getStatus() : EventoStatus.AGENDADO);
        return evento;
    }

    private LocalDateTime calcularDataInicio(String periodo) {
        LocalDateTime agora = LocalDateTime.now();
        return switch (periodo) {
            case "hoje" -> agora.withHour(0).withMinute(0).withSecond(0).withNano(0);
            case "7d" -> agora.minusDays(7);
            case "30d" -> agora.minusDays(30);
            default -> agora.minusDays(30);
        };
    }
}
