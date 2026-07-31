package com.baronesa.emporio.dynamicform.controller;

import com.baronesa.emporio.dynamicform.service.DynamicFormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/form-builder")
@RequiredArgsConstructor
@Slf4j
public class DynamicFormPublicController {

    private final DynamicFormService dynamicFormService;

    @GetMapping("/available-entities")
    public ResponseEntity<List<Map<String, String>>> listAvailableEntities() {
        log.info("Listando entidades disponíveis para formulário dinâmico");
        List<Map<String, String>> entities = dynamicFormService.getAvailableEntityTypes();
        return ResponseEntity.ok(entities);
    }
}
