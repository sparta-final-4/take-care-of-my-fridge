package org.example.takecareofmyfridgebe.domain.chat.repository;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByIdAndMemberId(Long id, Long memberId);

    Optional<ChatSession> findByMemberIdAndStatus(Long memberId, ChatSessionStatus status);
}
