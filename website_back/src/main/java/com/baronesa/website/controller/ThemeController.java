package com.baronesa.website.controller;

import com.baronesa.website.dto.ThemeDTO;
import com.baronesa.website.dto.ThemeResponseDTO;
import com.baronesa.website.dto.ThemeScheduleDTO;
import com.baronesa.website.service.ThemeService;
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
@RequestMapping("/api/themes")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Temas", description = "API para gerenciamento de temas e personalização visual")
public class ThemeController {

    private final ThemeService themeService;

    /**
     * GET /api/themes/active
     * Retorna o tema ativo para o tenant (público - para uso no frontend)
     */
    @Operation(
            summary = "Obter tema ativo",
            description = "Retorna o tema ativo para o tenant especificado. Considera temas agendados e ativos. Endpoint público para uso no frontend.",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tema ativo retornado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ThemeResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Nenhum tema encontrado para o tenant", content = @Content)
    })
    @GetMapping("/active")
    public ResponseEntity<ThemeResponseDTO> getActiveTheme(
            @Parameter(description = "ID do tenant", required = true) @RequestParam String tenantId) {
        log.info("GET /api/themes/active - Tenant: {}", tenantId);
        ThemeResponseDTO theme = themeService.getActiveTheme(tenantId);
        return ResponseEntity.ok(theme);
    }

    /**
     * GET /api/public/theme/active
     * Retorna o tema ativo para o tenant (público - para uso direto no frontend)
     * Similar ao endpoint de eventos, sem autenticação necessária
     */
    @Operation(
            summary = "Obter tema ativo (Público)",
            description = "Retorna o tema ativo para o tenant especificado. Endpoint público sem autenticação, similar ao de eventos.",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tema ativo retornado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ThemeResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Nenhum tema encontrado para o tenant", content = @Content)
    })
    @GetMapping("/public/theme/active")
    public ResponseEntity<ThemeResponseDTO> getActiveThemePublic(
            @Parameter(description = "ID do tenant", required = false) @RequestParam(required = false) String tenantId) {
        String effectiveTenantId = (tenantId != null && !tenantId.isBlank())
                ? tenantId
                : themeService.getDefaultTenantId();

        log.info("GET /api/public/theme/active - Tenant: {}", effectiveTenantId);
        ThemeResponseDTO theme = themeService.getActiveTheme(effectiveTenantId);

        // Adicionando cache por 5 minutos (300 segundos)
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300")
                .body(theme);
    }

    /**
     * GET /api/themes
     * Lista todos os temas de um tenant (admin)
     */
    @Operation(
            summary = "Listar temas do tenant (requer autenticação)",
            description = "Retorna todos os temas associados ao tenant especificado. Requer autenticação.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de temas retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ThemeResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content)
    })
    @GetMapping
    public ResponseEntity<List<ThemeResponseDTO>> getThemes(
            @Parameter(description = "ID do tenant", required = true) @RequestParam String tenantId) {
        log.info("GET /api/themes - Tenant: {}", tenantId);
        List<ThemeResponseDTO> themes = themeService.getThemesByTenant(tenantId);
        return ResponseEntity.ok(themes);
    }

    /**
     * GET /api/themes/{id}
     * Retorna tema por ID (requer autenticação)
     */
    @Operation(
            summary = "Buscar tema por ID (requer autenticação)",
            description = "Retorna os detalhes de um tema específico pelo seu ID. Requer autenticação.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tema encontrado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ThemeResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Tema não encontrado", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<ThemeResponseDTO> getThemeById(
            @Parameter(description = "ID do tema", required = true) @PathVariable Long id) {
        log.info("GET /api/themes/{}", id);
        ThemeResponseDTO theme = themeService.getThemeById(id);
        return ResponseEntity.ok(theme);
    }

    /**
     * POST /api/themes
     * Cria um novo tema (requer autenticação)
     */
    @Operation(
            summary = "Criar novo tema (requer autenticação)",
            description = "Cria um novo tema no sistema. Requer autenticação.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tema criado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ThemeResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ThemeResponseDTO> createTheme(
            @Parameter(description = "Dados do tema a ser criado", required = true) @Valid @RequestBody ThemeDTO dto,
            @Parameter(description = "Reiniciar sistema após criar (para aplicar SEO)", required = false) @RequestParam(required = false, defaultValue = "false") boolean restart) {
        log.info("POST /api/themes - Criando tema: {}, restart={}", dto.getName(), restart);
        ThemeResponseDTO theme = themeService.createTheme(dto, restart);
        return ResponseEntity.status(HttpStatus.CREATED).body(theme);
    }

    /**
     * PUT /api/themes/{id}
     * Atualiza um tema existente (requer autenticação)
     */
    @Operation(
            summary = "Atualizar tema (requer autenticação)",
            description = "Atualiza os dados de um tema existente. Requer autenticação.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tema atualizado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ThemeResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Tema não encontrado", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<ThemeResponseDTO> updateTheme(
            @Parameter(description = "ID do tema a ser atualizado", required = true) @PathVariable Long id,
            @Parameter(description = "Novos dados do tema", required = true) @Valid @RequestBody ThemeDTO dto,
            @Parameter(description = "Reiniciar sistema após atualizar (para aplicar SEO)", required = false) @RequestParam(required = false, defaultValue = "false") boolean restart) {
        log.info("PUT /api/themes/{} - Atualizando tema, restart={}", id, restart);
        ThemeResponseDTO theme = themeService.updateTheme(id, dto, restart);
        return ResponseEntity.ok(theme);
    }

    /**
     * DELETE /api/themes/{id}
     * Exclui um tema (requer autenticação)
     */
    @Operation(
            summary = "Excluir tema (requer autenticação)",
            description = "Exclui permanentemente um tema do sistema. Requer autenticação.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tema excluído com sucesso", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Tema não encontrado", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTheme(
            @Parameter(description = "ID do tema a ser excluído", required = true) @PathVariable Long id) {
        log.info("DELETE /api/themes/{} - Excluindo tema", id);
        themeService.deleteTheme(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/themes/{id}/duplicate
     * Duplica um tema existente (requer autenticação)
     */
    @Operation(
            summary = "Duplicar tema (requer autenticação)",
            description = "Cria uma cópia de um tema existente com um novo nome. Requer autenticação.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tema duplicado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ThemeResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Tema não encontrado", content = @Content)
    })
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ThemeResponseDTO> duplicateTheme(
            @Parameter(description = "ID do tema a ser duplicado", required = true) @PathVariable Long id,
            @Parameter(description = "Novo nome para o tema duplicado", required = true) @RequestParam String newName,
            @Parameter(description = "ID do tenant para o novo tema", required = true) @RequestParam String newTenantId) {
        log.info("POST /api/themes/{}/duplicate - Duplicando tema, novo nome: {}", id, newName);
        ThemeResponseDTO duplicatedTheme = themeService.duplicateTheme(id, newName, newTenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(duplicatedTheme);
    }

    /**
     * POST /api/themes/{id}/schedule
     * Agenda um tema para ativação em datas específicas (requer autenticação)
     */
    @Operation(
            summary = "Agendar tema (requer autenticação)",
            description = "Agenda um tema para ativação em datas específicas (ex: temas sazonais). Requer autenticação.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tema agendado com sucesso", content = @Content),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado - Token inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Tema não encontrado", content = @Content)
    })
    @PostMapping("/{id}/schedule")
    public ResponseEntity<Void> scheduleTheme(
            @Parameter(description = "ID do tema a ser agendado", required = true) @PathVariable Long id,
            @Parameter(description = "Dados de agendamento", required = true) @Valid @RequestBody ThemeScheduleDTO scheduleDTO) {
        log.info("POST /api/themes/{}/schedule - Agendando tema", id);
        themeService.scheduleTheme(id, scheduleDTO);
        return ResponseEntity.ok().build();
    }
}
