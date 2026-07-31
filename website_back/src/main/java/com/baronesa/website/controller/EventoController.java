package com.baronesa.website.controller;

import com.baronesa.website.dto.EventoDTO;
import com.baronesa.website.dto.EventoResponseDTO;
import com.baronesa.website.dto.PageResponse;
import com.baronesa.website.enums.EventoStatus;
import com.baronesa.website.service.EventoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Eventos", description = "API para gerenciamento de eventos e shows do Villa Custom Viking Pub")
public class EventoController {

    private final EventoService eventoService;

    /**
     * GET /api/eventos/proximos
     * Retorna os próximos 4 eventos futuros (público - para home page)
     */
    @Operation(
            summary = "Listar próximos eventos",
            description = "Retorna os próximos 4 eventos futuros ordenados por data. Endpoint público para exibição na home page.",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de eventos retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventoResponseDTO.class)))
    })
    @GetMapping("/proximos")
    public ResponseEntity<List<EventoResponseDTO>> listarProximosEventos() {
        log.info("GET /api/eventos/proximos");
        List<EventoResponseDTO> eventos = eventoService.listarProximosEventos();
        return ResponseEntity.ok(eventos);
    }

    /**
     * GET /api/eventos/dashboard
     * Retorna eventos para composição do dashboard da Villa
     */
    @Operation(
            summary = "Listar eventos para dashboard",
            description = "Retorna os eventos do período solicitado para que o dashboard de faturamento possa cruzar com os pagamentos locais.",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de eventos do dashboard retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventoResponseDTO.class)))
    })
    @GetMapping("/dashboard")
    public ResponseEntity<List<EventoResponseDTO>> listarEventosDashboard(
            @Parameter(description = "Período para filtrar eventos (hoje, 7d ou 30d)", example = "30d") @RequestParam(defaultValue = "30d") String periodo) {
        log.info("GET /api/eventos/dashboard - Período: {}", periodo);
        List<EventoResponseDTO> eventos = eventoService.listarEventosParaDashboard(periodo);
        return ResponseEntity.ok(eventos);
    }

    /**
     * GET /api/eventos/realizados
     * Retorna eventos realizados (público - para home page)
     */
    @Operation(
            summary = "Listar eventos realizados",
            description = "Retorna todos os eventos com status REALIZADO. Endpoint público para seção 'Shows Realizados' na home page.",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de eventos realizados retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventoResponseDTO.class)))
    })
    @GetMapping("/realizados")
    public ResponseEntity<List<EventoResponseDTO>> listarEventosRealizados() {
        log.info("GET /api/eventos/realizados");
        List<EventoResponseDTO> eventos = eventoService.listarEventosRealizados();
        return ResponseEntity.ok(eventos);
    }

    /**
     * GET /api/eventos/{id}
     * Retorna evento por ID (público)
     */
    @Operation(
            summary = "Buscar evento por ID",
            description = "Retorna os detalhes de um evento específico pelo seu ID. Endpoint público.",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Evento encontrado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventoResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<EventoResponseDTO> buscarPorId(
            @Parameter(description = "ID do evento", required = true) @PathVariable Long id) {
        log.info("GET /api/eventos/{}", id);
        EventoResponseDTO evento = eventoService.buscarPorId(id);
        return ResponseEntity.ok(evento);
    }

    /**
     * GET /api/eventos
     * Lista todos os eventos com filtro opcional por status (admin)
     */
    @Operation(
            summary = "Listar todos os eventos (Admin)",
            description = "Retorna todos os eventos cadastrados com filtro opcional por status. Requer autenticação com roles ADMIN, SYSTEM ou FUNCIONARIO.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de eventos retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventoResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado - Permissões insuficientes", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM', 'FUNCIONARIO')")
    public ResponseEntity<List<EventoResponseDTO>> listarTodos(
            @Parameter(description = "Filtrar por status do evento (opcional)") @RequestParam(required = false) EventoStatus status) {
        log.info("GET /api/eventos - Status: {}", status);
        List<EventoResponseDTO> eventos = eventoService.listarTodos(status);
        return ResponseEntity.ok(eventos);
    }

    /**
     * GET /api/eventos/admin
     * Lista todos os eventos com paginação e busca (admin)
     */
    @Operation(
            summary = "Listar eventos com paginação e busca (Admin)",
            description = "Retorna eventos cadastrados com suporte a paginação e busca por título, banda ou gênero. Requer autenticação com roles ADMIN, SYSTEM ou FUNCIONARIO.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página de eventos retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado - Permissões insuficientes", content = @Content)
    })
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM', 'FUNCIONARIO')")
    public ResponseEntity<PageResponse<EventoResponseDTO>> listarComPaginacao(
            @Parameter(description = "Número da página (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Termo de busca (título, banda ou gênero)") @RequestParam(required = false) String search,
            @Parameter(description = "Filtrar por status do evento") @RequestParam(required = false) EventoStatus status) {
        log.info("GET /api/eventos/admin - Página: {}, Tamanho: {}, Busca: {}, Status: {}", page, size, search, status);
        PageResponse<EventoResponseDTO> pageResponse = eventoService.listarComPaginacao(page, size, search, status);
        return ResponseEntity.ok(pageResponse);
    }

    /**
     * POST /api/eventos
     * Cria um novo evento (admin)
     */
    @Operation(
            summary = "Criar novo evento (Admin)",
            description = "Cria um novo evento no sistema. Requer autenticação com roles ADMIN ou SYSTEM.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Evento criado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventoResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado - Permissões insuficientes", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<EventoResponseDTO> criar(
            @Parameter(description = "Dados do evento a ser criado", required = true) @Valid @RequestBody EventoDTO dto) {
        log.info("POST /api/eventos - Criando evento: {}", dto.getTitulo());
        EventoResponseDTO evento = eventoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(evento);
    }

    /**
     * PUT /api/eventos/{id}
     * Atualiza um evento existente (admin)
     */
    @Operation(
            summary = "Atualizar evento (Admin)",
            description = "Atualiza os dados de um evento existente. Requer autenticação com roles ADMIN ou SYSTEM.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Evento atualizado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventoResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado - Permissões insuficientes", content = @Content),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado", content = @Content)
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<EventoResponseDTO> atualizar(
            @Parameter(description = "ID do evento a ser atualizado", required = true) @PathVariable Long id,
            @Parameter(description = "Novos dados do evento", required = true) @Valid @RequestBody EventoDTO dto) {
        log.info("PUT /api/eventos/{} - Atualizando evento", id);
        EventoResponseDTO evento = eventoService.atualizar(id, dto);
        return ResponseEntity.ok(evento);
    }

    /**
     * DELETE /api/eventos/{id}
     * Exclui (soft delete) um evento (admin)
     */
    @Operation(
            summary = "Excluir evento (Admin)",
            description = "Realiza soft delete de um evento (marca como inativo). Requer autenticação com roles ADMIN ou SYSTEM.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Evento excluído com sucesso", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado - Permissões insuficientes", content = @Content),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado", content = @Content)
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID do evento a ser excluído", required = true) @PathVariable Long id) {
        log.info("DELETE /api/eventos/{} - Excluindo evento", id);
        eventoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
