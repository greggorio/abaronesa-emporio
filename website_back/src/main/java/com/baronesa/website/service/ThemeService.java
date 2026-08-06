package com.baronesa.website.service;

import com.baronesa.website.dto.*;
import com.baronesa.website.entity.TenantConfig;
import com.baronesa.website.entity.Theme;
import com.baronesa.website.entity.ThemeAssignment;
import com.baronesa.website.enums.ThemeStatus;
import com.baronesa.website.repository.TenantConfigRepository;
import com.baronesa.website.repository.ThemeAssignmentRepository;
import com.baronesa.website.repository.ThemeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThemeService {

    private final ThemeRepository themeRepository;
    private final ThemeAssignmentRepository themeAssignmentRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final ThemeNotificationService themeNotificationService;
    private final RedeployService redeployService;
    private final WebClient webClient = WebClient.builder().build();

    @Value("${app.default-tenant-id:baronesa}")
    private String fallbackDefaultTenantId;

    @Value("${app.fallback-tenant-ids:}")
    private String fallbackTenantIdsRaw;

    @Value("${erp.api.url:http://localhost:8080}")
    private String erpApiUrl;

    private static final String DEFAULT_TENANT_CONFIG_KEY = "default_tenant_id";

    @Transactional(readOnly = true)
    public List<ThemeResponseDTO> getThemesByTenant(String tenantId) {
        List<Theme> themes = themeRepository.findByTenantId(tenantId);
        return themes.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ThemeResponseDTO getActiveTheme(String tenantId) {
        ThemeResponseDTO theme = findActiveThemeForTenant(tenantId);
        if (theme != null) {
            return theme;
        }

        for (String fallbackTenantId : getFallbackTenantIds()) {
            if (fallbackTenantId.equalsIgnoreCase(tenantId)) {
                continue;
            }
            ThemeResponseDTO fallbackTheme = findActiveThemeForTenant(fallbackTenantId);
            if (fallbackTheme != null) {
                return fallbackTheme;
            }
        }

        throw new RuntimeException("Nenhum tema publicado encontrado para o tenant: " + tenantId +
                ". Crie pelo menos um tema para este tenant.");
    }

    @Transactional(readOnly = true)
    public ThemeResponseDTO getThemeById(Long id) {
        Theme theme = themeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tema não encontrado: " + id));
        return toResponseDTO(theme);
    }

    @Transactional(readOnly = true)
    public String getDefaultTenantId() {
        return tenantConfigRepository.findById(DEFAULT_TENANT_CONFIG_KEY)
                .map(TenantConfig::getValue)
                .orElse(fallbackDefaultTenantId);
    }

    private ThemeResponseDTO findActiveThemeForTenant(String tenantId) {
        List<ThemeAssignment> activeAssignments = themeAssignmentRepository
                .findActiveThemeAssignments(tenantId, LocalDateTime.now());

        if (!activeAssignments.isEmpty()) {
            ThemeAssignment activeAssignment = activeAssignments.get(0);
            return getThemeById(activeAssignment.getThemeId());
        }

        List<Theme> publishedThemes = themeRepository.findByTenantIdAndStatus(tenantId, ThemeStatus.PUBLISHED);
        if (!publishedThemes.isEmpty()) {
            return publishedThemes.stream()
                    .max((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                    .map(this::toResponseDTO)
                    .orElse(null);
        }

        return null;
    }

    private List<String> getFallbackTenantIds() {
        if (fallbackTenantIdsRaw == null || fallbackTenantIdsRaw.isBlank()) {
            return List.of();
        }
        return List.of(fallbackTenantIdsRaw.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    @Transactional
    public void setDefaultTenantId(String tenantId) {
        TenantConfig config = tenantConfigRepository.findById(DEFAULT_TENANT_CONFIG_KEY)
                .orElse(new TenantConfig(DEFAULT_TENANT_CONFIG_KEY, tenantId, LocalDateTime.now()));
        config.setValue(tenantId);
        config.setUpdatedAt(LocalDateTime.now());
        tenantConfigRepository.save(config);
    }

    @Transactional
    public ThemeResponseDTO createTheme(ThemeDTO dto, boolean restart) {
        // Verificar se o tema base existe (se informado)
        if (dto.getBaseThemeId() != null) {
            themeRepository.findById(dto.getBaseThemeId())
                    .orElseThrow(() -> new RuntimeException("Tema base não encontrado: " + dto.getBaseThemeId()));
        }

        Theme theme = new Theme();
        theme.setName(dto.getName());
        theme.setBaseThemeId(dto.getBaseThemeId());
        theme.setStatus(dto.getStatus() != null ? dto.getStatus() : ThemeStatus.DRAFT);
        theme.setTokens(dto.getTokens());
        theme.setAssets(dto.getAssets());
        theme.setContent(dto.getContent());
        theme.setTenantId(dto.getTenantId());

        Theme saved = themeRepository.save(theme);

        if (restart) {
            redeployService.triggerRedeploy(saved.getTenantId());
        }

        return toResponseDTO(saved);
    }

    @Transactional
    public ThemeResponseDTO updateTheme(Long id, ThemeDTO dto, boolean restart) {
        Theme theme = themeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tema não encontrado: " + id));

        theme.setName(dto.getName());
        theme.setBaseThemeId(dto.getBaseThemeId());
        theme.setStatus(dto.getStatus());
        theme.setTokens(dto.getTokens());
        theme.setAssets(dto.getAssets());
        theme.setContent(dto.getContent());

        Theme updated = themeRepository.save(theme);

        // Notificar ERP para marcar traduções pendentes do tema
        triggerErpThemeMarkTranslations(theme.getTenantId());

        if (restart) {
            redeployService.triggerRedeploy(updated.getTenantId());
        }

        return toResponseDTO(updated);
    }

    @Transactional
    public void deleteTheme(Long id) {
        Theme theme = themeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tema não encontrado: " + id));

        // Verificar se o tema está atribuído a algum tenant antes de excluir
        List<ThemeAssignment> assignments = themeAssignmentRepository.findByTenantIdAndThemeId(theme.getTenantId(), id);
        if (!assignments.isEmpty()) {
            throw new RuntimeException("Não é possível excluir o tema pois está atribuído a um ou mais tenants");
        }

        themeRepository.deleteById(id);
    }

    @Transactional
    public ThemeResponseDTO duplicateTheme(Long id, String newName, String newTenantId) {
        Theme original = themeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tema não encontrado: " + id));

        Theme duplicated = new Theme();
        duplicated.setName(newName);
        duplicated.setBaseThemeId(original.getId()); // O tema original se torna o tema base do duplicado
        duplicated.setStatus(ThemeStatus.DRAFT);
        duplicated.setTokens(original.getTokens());
        duplicated.setAssets(original.getAssets());
        duplicated.setContent(original.getContent());
        duplicated.setTenantId(newTenantId);

        Theme saved = themeRepository.save(duplicated);
        return toResponseDTO(saved);
    }

    @Transactional
    public void scheduleTheme(Long themeId, ThemeScheduleDTO scheduleDTO) {
        Theme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new RuntimeException("Tema não encontrado: " + themeId));

        // Desativar qualquer tema ativo antes de salvar o novo
        themeAssignmentRepository.deactivateAssignmentsForTenant(theme.getTenantId());
        // Remover agendamentos antigos para este tema
        List<ThemeAssignment> existingAssignments = themeAssignmentRepository
                .findByTenantIdAndThemeId(theme.getTenantId(), themeId);
        themeAssignmentRepository.deleteAll(existingAssignments);

        // Criar novo agendamento
        ThemeAssignment assignment = new ThemeAssignment();
        assignment.setTenantId(theme.getTenantId());
        assignment.setThemeId(themeId);
        assignment.setValidFrom(scheduleDTO.getValidFrom());
        assignment.setValidTo(scheduleDTO.getValidTo());
        assignment.setPriority(scheduleDTO.getPriority() != null ? scheduleDTO.getPriority() : 0);
        assignment.setIsActive(true);

        themeAssignmentRepository.save(assignment);
        setDefaultTenantId(theme.getTenantId());
        themeNotificationService.broadcastThemeChange(theme.getTenantId());
    }

    private ThemeResponseDTO toResponseDTO(Theme theme) {
        ThemeResponseDTO dto = new ThemeResponseDTO();
        dto.setId(theme.getId());
        dto.setName(theme.getName());
        dto.setBaseThemeId(theme.getBaseThemeId());
        dto.setStatus(theme.getStatus());
        dto.setTokens(theme.getTokens());
        dto.setAssets(theme.getAssets());
        dto.setContent(theme.getContent());
        dto.setTenantId(theme.getTenantId());
        dto.setCreatedAt(theme.getCreatedAt());
        dto.setUpdatedAt(theme.getUpdatedAt());
        return dto;
    }

    /**
     * Notifica o ERP para marcar traduções PENDING do tema, via header X-Mark-Translations.
     * Ignora erros (log de warning) para não quebrar o fluxo de atualização do tema.
     */
    private void triggerErpThemeMarkTranslations(String tenantId) {
        try {
            String url = erpApiUrl + "/api/website/themes/public/theme/active?tenantId=" + tenantId;
            webClient.get()
                    .uri(url)
                    .header("X-Mark-Translations", "true")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("ERP notified to mark theme translations for tenant {}", tenantId);
        } catch (Exception e) {
            log.warn("Failed to notify ERP to mark theme translations for tenant {}: {}", tenantId, e.getMessage());
        }
    }
}
