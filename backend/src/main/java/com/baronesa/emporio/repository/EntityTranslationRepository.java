package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.EntityTranslation;
import com.baronesa.emporio.entity.TranslationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EntityTranslationRepository extends JpaRepository<EntityTranslation, Long> {

    @Query("SELECT t FROM EntityTranslation t " +
            "WHERE t.entityType = :entityType AND t.entityId = :entityId AND t.field = :field " +
            "AND t.locale = :locale AND t.status IN :statuses")
    Optional<EntityTranslation> findOneActive(@Param("entityType") String entityType,
                                              @Param("entityId") Long entityId,
                                              @Param("field") String field,
                                              @Param("locale") String locale,
                                              @Param("statuses") List<TranslationStatus> statuses);

    List<EntityTranslation> findByEntityTypeAndEntityIdAndField(String entityType, Long entityId, String field);

    @Query("""
        SELECT t FROM EntityTranslation t
        WHERE (:locale IS NULL OR t.locale = :locale)
          AND (:status IS NULL OR t.status = :status)
          AND (:entityType IS NULL OR t.entityType = :entityType)
          AND (:entityId IS NULL OR t.entityId = :entityId)
          AND (:field IS NULL OR t.field = :field)
          AND (
            :search IS NULL OR :search = '' OR
            LOWER(t.sourceText) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(t.translatedText) LIKE LOWER(CONCAT('%', :search, '%'))
          )
        ORDER BY t.updatedAt DESC
        """)
    Page<EntityTranslation> search(@Param("locale") String locale,
                                   @Param("status") TranslationStatus status,
                                   @Param("entityType") String entityType,
                                   @Param("entityId") Long entityId,
                                   @Param("field") String field,
                                   @Param("search") String search,
                                   Pageable pageable);
}
