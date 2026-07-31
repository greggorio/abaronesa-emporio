package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.SignageTemplateElementsResponseDTO;
import com.baronesa.emporio.dto.SignageTemplateListDTO;
import com.baronesa.emporio.dto.SignageTemplateResponseDTO;
import com.baronesa.emporio.dto.SignageTemplateUpdateRequestDTO;
import com.baronesa.emporio.service.SignageTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/signage/templates")
@RequiredArgsConstructor
public class SignageTemplateController {

    private final SignageTemplateService signageTemplateService;

    @GetMapping
    public ResponseEntity<List<SignageTemplateListDTO>> listarTemplatesAtivos() {
        List<SignageTemplateListDTO> templates = signageTemplateService.listActiveTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<SignageTemplateResponseDTO> buscarDetalhes(@PathVariable String templateId) {
        SignageTemplateResponseDTO template = signageTemplateService.getTemplateDetails(templateId);
        return ResponseEntity.ok(template);
    }

    @GetMapping("/{templateId}/elements")
    public ResponseEntity<SignageTemplateElementsResponseDTO> buscarElementos(@PathVariable String templateId) {
        SignageTemplateElementsResponseDTO elements = signageTemplateService.getTemplateElements(templateId);
        return ResponseEntity.ok(elements);
    }

    @PatchMapping("/{templateId}")
    public ResponseEntity<SignageTemplateResponseDTO> atualizarTemplate(@PathVariable String templateId,
                                                                        @RequestBody SignageTemplateUpdateRequestDTO request) {
        SignageTemplateResponseDTO template = signageTemplateService.updateTemplate(templateId, request);
        return ResponseEntity.ok(template);
    }
}
