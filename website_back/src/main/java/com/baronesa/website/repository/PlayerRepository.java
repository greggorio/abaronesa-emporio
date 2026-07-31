package com.baronesa.website.repository;

import com.baronesa.website.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findBySessionCodeAndActiveTrue(String sessionCode);

    List<Player> findBySessionCodeAndActiveTrueOrderByScoreDesc(String sessionCode);

    Optional<Player> findByConnectionId(String connectionId);

    boolean existsBySessionCodeAndNickname(String sessionCode, String nickname);
}
