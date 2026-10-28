package org.example.takecareofmyfridgebe.domain.chat.repository;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT MAX(m.sequence) FROM ChatMessage m WHERE m.session.id = :sessionId")
    Optional<Integer> findMaxSequenceBySessionId(@Param("sessionId") Long sessionId);

    List<ChatMessage> findBySessionIdOrderBySequenceAsc(Long sessionId);
}
