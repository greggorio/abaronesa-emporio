package com.baronesa.emporio.dynamicform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.config.HybridFormConfigRegistry;
import com.baronesa.emporio.dynamicform.dto.ActionDTO;
import com.baronesa.emporio.dynamicform.dto.FormDefinitionDTO;
import com.baronesa.emporio.dynamicform.dto.TabDTO;
import com.baronesa.emporio.dynamicform.dto.TableColumnDTO;
import com.baronesa.emporio.dynamicform.entity.DynamicFormDefinition;
import com.baronesa.emporio.dynamicform.service.DynamicFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/form-builder")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Form Builder", description = "API para gerenciamento dinâmico de formulários")
//@PreAuthorize("hasRole('ADMIN')") // Apenas administradores podem gerenciar formulários
public class FormBuilderController {

    private final DynamicFormService dynamicFormService;
    private final HybridFormConfigRegistry registry;

    @Operation(summary = "Lista todas as definições de formulário ativas")
    @GetMapping("/definitions")
    public ResponseEntity<List<DynamicFormDefinition>> listDefinitions() {
        return ResponseEntity.ok(dynamicFormService.findAllActive());
    }

//    @Operation(summary = "Obtém uma definição específica")
//    @GetMapping("/definitions/{entityType}")
//    public ResponseEntity<DynamicFormDefinition> getDefinition(@PathVariable String entityType) {
//        return dynamicFormService.findByEntityType(entityType)
//                .map(ResponseEntity::ok)
//                .orElse(ResponseEntity.notFound().build());
//    }

    @Operation(summary = "Cria ou atualiza uma definição de formulário")
    @PostMapping("/definitions")
    public ResponseEntity<DynamicFormDefinition> saveDefinition(@Valid @RequestBody FormDefinitionDTO dto) {
        log.info("Salvando definição de formulário: {}", dto.getEntityType());
        DynamicFormDefinition saved = dynamicFormService.save(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "Salva ou atualiza uma definição e retorna o DTO completo")
    @PostMapping("/definitions/save")
    public ResponseEntity<FormDefinitionDTO> saveAndReturnDTO(@Valid @RequestBody FormDefinitionDTO dto) {
        log.info("Salvando (com retorno) a definição: {}", dto.getEntityType());
        DynamicFormDefinition saved = dynamicFormService.save(dto);

        // Reutiliza o mesmo processo de montagem do DTO
        return getDefinition(saved.getEntityType());
    }


    @Operation(summary = "Preview da configuração do formulário")
    @GetMapping("/preview/{entityType}")
    public ResponseEntity<Map<String, Object>> previewForm(@PathVariable String entityType) {
        try {
            BaseFormConfig config = registry.getConfig(entityType);
            Map<String, Object> preview = new HashMap<>();

            // Dados básicos
            preview.put("programName", config.getProgramName());
            preview.put("programIcon", config.getProgramIcon());
            preview.put("entityType", entityType);

            // Usar o método createResponse do BaseFormConfig que já faz tudo isso
            Map<String, Object> fullResponse = config.createResponse(List.of());

            // Extrair o que precisamos do response completo
            if (fullResponse.containsKey("form_definitions_new")) {
                preview.put("formDefinitions", fullResponse.get("form_definitions_new"));
            }

            if (fullResponse.containsKey("form_botoes")) {
                preview.put("actions", fullResponse.get("form_botoes"));
            }

            preview.put("tableColumns", config.getTableColumns());
            preview.put("isDynamic", dynamicFormService.existsActiveDefinition(entityType));

            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.error("Erro ao gerar preview para: {}", entityType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Desativa uma definição de formulário")
    @DeleteMapping("/definitions/{entityType}")
    public ResponseEntity<Void> deactivateDefinition(@PathVariable String entityType) {
        dynamicFormService.deactivate(entityType);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lista todos os tipos de entidade disponíveis")
    @GetMapping("/available-types")
    public ResponseEntity<Map<String, String>> getAvailableTypes() {
        return ResponseEntity.ok(registry.getAllAvailableTypes());
    }

    @Operation(summary = "Detecta os campos disponíveis para uma entidade")
    @GetMapping("/detect-fields/{entityType}")
    public ResponseEntity<List<Map<String, Object>>> detectFields(@PathVariable String entityType) {
        try {
            List<Map<String, Object>> fields = dynamicFormService.detectFieldsForEntity(entityType);
            return ResponseEntity.ok(fields);
        } catch (Exception e) {
            log.error("Erro ao detectar campos para entidade: {}", entityType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of(Map.of("error", e.getMessage())));
        }
    }


    @Operation(summary = "Limpa o cache de configurações")
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, String>> clearCache() {
        dynamicFormService.clearCache();
        return ResponseEntity.ok(Map.of("message", "Cache limpo com sucesso"));
    }

    @Operation(summary = "Lista entidades com formulários dinâmicos disponíveis")
    @GetMapping("/entities")
    public ResponseEntity<List<Map<String, Object>>> listAvailableEntities() {
        List<DynamicFormDefinition> definitions = dynamicFormService.findAllActive();

        List<Map<String, Object>> response = definitions.stream().map(def -> {
            Map<String, Object> map = new HashMap<>();
            map.put("entityType", def.getEntityType());
            map.put("programName", def.getProgramName());
            map.put("complexity", def.getComplexity());
            map.put("updatedAt", def.getUpdatedAt());
            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/definitions/{entityType}")
    public ResponseEntity<FormDefinitionDTO> getDefinition(@PathVariable String entityType) {
        Optional<DynamicFormDefinition> definitionOpt = dynamicFormService.findByEntityType(entityType);

        // Se não encontrar, retorna uma definição vazia/padrão
        if (definitionOpt.isEmpty()) {
            FormDefinitionDTO emptyDto = new FormDefinitionDTO();
            emptyDto.setEntityType(entityType);
            // Opcionalmente, você pode preencher com valores padrão
            emptyDto.setTabs(new ArrayList<>());
            emptyDto.setActions(new ArrayList<>());
            emptyDto.setTableColumns(new ArrayList<>());
            emptyDto.setCustomSlots(new HashMap<>());

            return ResponseEntity.ok(emptyDto);
        }

        // Se encontrar, processa normalmente
        DynamicFormDefinition definition = definitionOpt.get();
        FormDefinitionDTO dto = new FormDefinitionDTO();

        dto.setEntityType(definition.getEntityType());
        dto.setProgramName(definition.getProgramName());
        dto.setProgramIcon(definition.getProgramIcon());
        dto.setComplexity(definition.getComplexity());
        dto.setTableOrder(definition.getTableOrder());
        dto.setJavaExtensionClass(definition.getJavaExtensionClass());
        dto.setDialogConfig(definition.getDialogConfig());

        // Parse estrutura do formulário
        Map<String, Object> structure = definition.getFormStructure();
        if (structure != null) {
            Object tabsRaw = structure.get("tabs");
            Object actionsRaw = structure.get("actions");

            if (tabsRaw instanceof List<?> tabsList) {
                List<TabDTO> tabs = new ArrayList<>();
                for (Object tabObj : tabsList) {
                    tabs.add(new ObjectMapper().convertValue(tabObj, TabDTO.class));
                }
                dto.setTabs(tabs);
            }

            if (actionsRaw instanceof List<?> actionsList) {
                List<ActionDTO> actions = new ArrayList<>();
                for (Object actionObj : actionsList) {
                    actions.add(new ObjectMapper().convertValue(actionObj, ActionDTO.class));
                }
                dto.setActions(actions);
            }
        }

        // Parse colunas da tabela
        Map<String, Object> tableColumns = definition.getTableColumns();
        if (tableColumns != null && tableColumns.containsKey("columns")) {
            Object colsRaw = tableColumns.get("columns");
            if (colsRaw instanceof List<?> colsList) {
                List<TableColumnDTO> columns = new ArrayList<>();
                for (Object colObj : colsList) {
                    columns.add(new ObjectMapper().convertValue(colObj, TableColumnDTO.class));
                }
                dto.setTableColumns(columns);
            }
        }

        dto.setCustomSlots(definition.getCustomSlots());

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/definitions/{entityType}/exists")
    public ResponseEntity<Boolean> checkDefinitionExists(@PathVariable String entityType) {
        boolean exists = dynamicFormService.existsActiveDefinition(entityType);
        return ResponseEntity.ok(exists);
    }

    @Operation(summary = "Recarrega configuração específica")
    @PostMapping("/cache/reload/{entityType}")
    public ResponseEntity<Map<String, String>> reloadConfig(@PathVariable String entityType) {
        registry.reloadConfig(entityType);
        return ResponseEntity.ok(Map.of(
                "message", "Configuração recarregada",
                "entityType", entityType
        ));
    }

    @Operation(summary = "Valida estrutura de formulário")
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateFormStructure(@RequestBody FormDefinitionDTO dto) {
        // TODO: Implementar validação completa
        Map<String, Object> result = Map.of(
                "valid", true,
                "errors", List.of(),
                "warnings", List.of()
        );
        return ResponseEntity.ok(result);
    }
}
