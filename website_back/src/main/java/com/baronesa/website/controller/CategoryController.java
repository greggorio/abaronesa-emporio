package com.baronesa.website.controller;

import com.baronesa.website.dto.CategoryCreateRequest;
import com.baronesa.website.dto.CategoryResponse;
import com.baronesa.website.dto.CategoryUpdateRequest;
import com.baronesa.website.enums.DifficultyLevel;
import com.baronesa.website.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "API para gerenciamento de categorias de perguntas")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Criar nova categoria")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        log.info("POST /api/categories - Criando categoria: {}", request.getName());
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar todas as categorias")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        log.debug("GET /api/categories - Listando todas as categorias");
        List<CategoryResponse> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/active")
    @Operation(summary = "Listar categorias ativas")
    public ResponseEntity<List<CategoryResponse>> getActiveCategories() {
        log.debug("GET /api/categories/active - Listando categorias ativas");
        List<CategoryResponse> categories = categoryService.getActiveCategories();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/difficulty/{level}")
    @Operation(summary = "Listar categorias por nível de dificuldade")
    public ResponseEntity<List<CategoryResponse>> getCategoriesByDifficulty(@PathVariable DifficultyLevel level) {
        log.debug("GET /api/categories/difficulty/{} - Listando categorias por dificuldade", level);
        List<CategoryResponse> categories = categoryService.getCategoriesByDifficulty(level);
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar categoria por ID")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        log.debug("GET /api/categories/{} - Buscando categoria", id);
        CategoryResponse response = categoryService.getCategoryById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar categoria")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        log.info("PUT /api/categories/{} - Atualizando categoria", id);
        CategoryResponse response = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Ativar/Desativar categoria")
    public ResponseEntity<CategoryResponse> toggleCategoryStatus(@PathVariable Long id) {
        log.info("PATCH /api/categories/{}/toggle - Alternando status da categoria", id);
        CategoryResponse response = categoryService.toggleCategoryStatus(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar categoria")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        log.info("DELETE /api/categories/{} - Deletando categoria", id);
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}

