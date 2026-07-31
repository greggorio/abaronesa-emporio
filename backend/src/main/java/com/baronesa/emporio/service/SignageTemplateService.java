package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.SignageTemplateAiConfigDTO;
import com.baronesa.emporio.dto.SignageTemplateElementDTO;
import com.baronesa.emporio.dto.SignageTemplateElementsResponseDTO;
import com.baronesa.emporio.dto.SignageTemplateListDTO;
import com.baronesa.emporio.dto.SignageTemplateResponseDTO;
import com.baronesa.emporio.dto.SignageTemplateUpdateRequestDTO;
import com.baronesa.emporio.entity.SignageTemplate;
import com.baronesa.emporio.repository.SignageTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SignageTemplateService {
    
    private final SignageTemplateRepository repository;
    
    /**
     * Busca configuração de IA de um template específico
     */
    public SignageTemplateAiConfigDTO getTemplateAiConfig(String templateId) {
        return repository.findByTemplateId(templateId)
            .map(this::toAiConfigDTO)
            .orElseThrow(() -> new RuntimeException("Template não encontrado: " + templateId));
    }
    
    /**
     * Lista todos os templates ativos (simplificado)
     */
    public List<SignageTemplateListDTO> listActiveTemplates() {
        return repository.findAllByIsActiveTrue().stream()
            .map(this::toListDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Busca detalhes completos de um template
     */
    public SignageTemplateResponseDTO getTemplateDetails(String templateId) {
        return repository.findByTemplateId(templateId)
            .map(this::toResponseDTO)
            .orElseThrow(() -> new RuntimeException("Template não encontrado: " + templateId));
    }
    
    private SignageTemplateAiConfigDTO toAiConfigDTO(SignageTemplate template) {
        return SignageTemplateAiConfigDTO.builder()
            .aiMode(template.getAiMode())
            .aiPrompt(template.getAiPrompt())
            .aiPromptVersion(template.getAiPromptVersion())
            .aiEnabled(template.getAiEnabled())
            .aiOutputSize(template.getAiOutputSize())
            .build();
    }
    
    private SignageTemplateListDTO toListDTO(SignageTemplate template) {
        return SignageTemplateListDTO.builder()
            .templateId(template.getTemplateId())
            .name(template.getName())
            .description(template.getDescription())
            .aiEnabled(template.getAiEnabled())
            .build();
    }
    
    private SignageTemplateResponseDTO toResponseDTO(SignageTemplate template) {
        return SignageTemplateResponseDTO.builder()
            .templateId(template.getTemplateId())
            .name(template.getName())
            .description(template.getDescription())
            .imageMode(template.getImageMode())
            .isActive(template.getIsActive())
            .aiEnabled(template.getAiEnabled())
            .aiMode(template.getAiMode())
            .aiPrompt(template.getAiPrompt())
            .aiPromptVersion(template.getAiPromptVersion())
            .aiOutputSize(template.getAiOutputSize())
            .build();
    }

    @Transactional
    public SignageTemplateResponseDTO updateTemplate(String templateId, SignageTemplateUpdateRequestDTO request) {
        SignageTemplate template = repository.findByTemplateId(templateId)
            .orElseThrow(() -> new RuntimeException("Template não encontrado: " + templateId));

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new RuntimeException("Nome do template não pode ser vazio");
            }
            template.setName(name);
        }

        if (request.getAiPrompt() != null) {
            String prompt = request.getAiPrompt().trim();
            template.setAiPrompt(prompt);
        }

        SignageTemplate saved = repository.save(template);
        return toResponseDTO(saved);
    }
    
    /**
     * Busca elementos configuráveis de um template para mapeamento de cores
     */
    public SignageTemplateElementsResponseDTO getTemplateElements(String templateId) {
        SignageTemplate template = repository.findByTemplateId(templateId)
            .orElseThrow(() -> new RuntimeException("Template não encontrado: " + templateId));
        
        List<SignageTemplateElementDTO> elements = parseColorSlots(template.getColorSlots(), templateId);
        
        return SignageTemplateElementsResponseDTO.builder()
            .templateId(template.getTemplateId())
            .name(template.getName())
            .elements(elements)
            .build();
    }
    
    /**
     * Converte o JSON color_slots em lista de elementos
     */
    private List<SignageTemplateElementDTO> parseColorSlots(String colorSlotsJson, String templateId) {
        List<SignageTemplateElementDTO> elements = new ArrayList<>();
        
        if (colorSlotsJson == null || colorSlotsJson.isEmpty()) {
            // Retorna elementos padrão se não houver color_slots definido
            return getDefaultElements(templateId);
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(colorSlotsJson);
            
            root.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                String paletteRef = entry.getValue().asText();
                
                elements.add(SignageTemplateElementDTO.builder()
                    .key(key)
                    .label(getElementLabel(key))
                    .description(getElementDescription(key))
                    .defaultSource("palette:" + paletteRef.toLowerCase())
                    .build());
            });
        } catch (Exception e) {
            log.warn("Erro ao parsear color_slots do template {}: {}", templateId, e.getMessage());
            return getDefaultElements(templateId);
        }
        
        return elements;
    }
    
    /**
     * Retorna elementos padrão baseado no template_id
     */
    private List<SignageTemplateElementDTO> getDefaultElements(String templateId) {
        List<SignageTemplateElementDTO> defaults = new ArrayList<>();
        
        // Elementos padrão comuns a todos os templates
        defaults.add(SignageTemplateElementDTO.builder()
            .key("background")
            .label("Fundo")
            .description("Cor de fundo do template")
            .defaultSource("palette:lightMuted")
            .build());
            
        defaults.add(SignageTemplateElementDTO.builder()
            .key("headline")
            .label("Título")
            .description("Cor do título principal")
            .defaultSource("palette:darkMuted")
            .build());
            
        defaults.add(SignageTemplateElementDTO.builder()
            .key("subtitle")
            .label("Subtítulo")
            .description("Cor do subtítulo")
            .defaultSource("palette:muted")
            .build());
            
        defaults.add(SignageTemplateElementDTO.builder()
            .key("price")
            .label("Preço")
            .description("Cor do preço")
            .defaultSource("palette:darkMuted")
            .build());
            
        defaults.add(SignageTemplateElementDTO.builder()
            .key("badge")
            .label("Badge")
            .description("Cor do badge/destaque")
            .defaultSource("palette:vibrant")
            .build());
        
        return defaults;
    }
    
    private String getElementLabel(String key) {
        Map<String, String> labels = Map.of(
            "background", "Fundo",
            "headline", "Título",
            "subtitle", "Subtítulo",
            "price", "Preço",
            "badge", "Badge",
            "separator", "Separador",
            "cta", "Botão de Ação",
            "text", "Texto"
        );
        return labels.getOrDefault(key, key.substring(0, 1).toUpperCase() + key.substring(1));
    }
    
    private String getElementDescription(String key) {
        Map<String, String> descriptions = Map.of(
            "background", "Cor de fundo do template",
            "headline", "Cor do título principal do produto",
            "subtitle", "Cor do subtítulo ou descrição",
            "price", "Cor do valor do preço",
            "badge", "Cor do badge de categoria ou destaque",
            "separator", "Cor do separador visual",
            "cta", "Cor do botão de call-to-action",
            "text", "Cor do texto geral"
        );
        return descriptions.getOrDefault(key, "Cor do elemento " + key);
    }
}
