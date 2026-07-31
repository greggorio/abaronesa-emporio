package com.baronesa.website.service;

import com.baronesa.website.dto.CategoryCreateRequest;
import com.baronesa.website.dto.CategoryResponse;
import com.baronesa.website.dto.CategoryUpdateRequest;
import com.baronesa.website.entity.Category;
import com.baronesa.website.enums.DifficultyLevel;
import com.baronesa.website.repository.CategoryRepository;
import com.baronesa.website.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final QuestionRepository questionRepository;

    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        log.info("Criando nova categoria: {}", request.getName());

        if (categoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Categoria com este nome já existe");
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .difficultyLevel(request.getDifficultyLevel())
                .icon(request.getIcon())
                .color(request.getColor())
                .active(true)
                .build();

        category = categoryRepository.save(category);
        return toCategoryResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(this::toCategoryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findAllActiveOrderedByName()
                .stream()
                .map(this::toCategoryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByDifficulty(DifficultyLevel level) {
        return categoryRepository.findByActiveTrueAndDifficultyLevel(level)
                .stream()
                .map(this::toCategoryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + id));
        return toCategoryResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        log.info("Atualizando categoria ID={}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + id));

        if (request.getName() != null) category.setName(request.getName());
        if (request.getDescription() != null) category.setDescription(request.getDescription());
        if (request.getDifficultyLevel() != null) category.setDifficultyLevel(request.getDifficultyLevel());
        if (request.getIcon() != null) category.setIcon(request.getIcon());
        if (request.getColor() != null) category.setColor(request.getColor());
        if (request.getActive() != null) category.setActive(request.getActive());

        category = categoryRepository.save(category);
        return toCategoryResponse(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        log.info("Deletando categoria ID={}", id);
        if (!categoryRepository.existsById(id)) {
            throw new IllegalArgumentException("Categoria não encontrada: " + id);
        }
        categoryRepository.deleteById(id);
    }

    @Transactional
    public CategoryResponse toggleCategoryStatus(Long id) {
        log.info("Alternando status da categoria ID={}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + id));
        category.setActive(!category.getActive());
        category = categoryRepository.save(category);
        return toCategoryResponse(category);
    }

    private CategoryResponse toCategoryResponse(Category category) {
        long questionCount = (category.getId() == null) ? 0 : questionRepository.countByCategoryId(category.getId());
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .difficultyLevel(category.getDifficultyLevel())
                .icon(category.getIcon())
                .color(category.getColor())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .questionCount(questionCount)
                .build();
    }
}

