package com.baronesa.website.repository;

import com.baronesa.website.entity.QuizSession;
import com.baronesa.website.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {

    Optional<QuizSession> findBySessionCode(String sessionCode);

    List<QuizSession> findByStatus(SessionStatus status);

    List<QuizSession> findByStatusIn(List<SessionStatus> statuses);
}
