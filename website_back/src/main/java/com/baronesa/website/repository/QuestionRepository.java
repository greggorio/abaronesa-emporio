package com.baronesa.website.repository;

import com.baronesa.website.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByActiveTrue();

    long countByActiveTrue();

    long countByCategoryId(Long categoryId);

    List<Question> findByCategoryIdAndActiveTrue(Long categoryId);

    @Query(value = "SELECT * FROM questions WHERE active = true ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Question> findRandomQuestions(@Param("limit") int limit);

    @Query(value = "SELECT * FROM questions WHERE category_id = :categoryId AND active = true ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Question> findRandomQuestionsByCategoryId(@Param("categoryId") Long categoryId, @Param("limit") int limit);

    @Query("SELECT q FROM Question q WHERE LOWER(q.question) = LOWER(:question) AND q.category.id = :categoryId")
    Optional<Question> findByQuestionAndCategoryId(@Param("question") String question, @Param("categoryId") Long categoryId);
}
