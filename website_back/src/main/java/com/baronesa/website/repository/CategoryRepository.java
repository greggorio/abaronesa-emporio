package com.baronesa.website.repository;

import com.baronesa.website.entity.Category;
import com.baronesa.website.enums.DifficultyLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    List<Category> findByActiveTrue();

    List<Category> findByDifficultyLevel(DifficultyLevel difficultyLevel);

    List<Category> findByActiveTrueAndDifficultyLevel(DifficultyLevel difficultyLevel);

    @Query("SELECT c FROM Category c WHERE c.active = true ORDER BY c.name ASC")
    List<Category> findAllActiveOrderedByName();

    boolean existsByName(String name);
}

