package org.example.takecareofmyfridgebe.domain.chat.repository;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessage;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long>, ChatMessageRepositoryCustom {

    @Query("SELECT MAX(m.sequence) FROM ChatMessage m WHERE m.session.id = :sessionId")
    Optional<Integer> findMaxSequenceBySessionId(@Param("sessionId") Long sessionId);

    List<ChatMessage> findBySessionIdOrderBySequenceAsc(Long sessionId);

    // 단어에 걸리지 않은 최근 메시지 6개 (최근 것부터). uk_chat_messages_chat_session_id_sequence 인덱스로 읽는다
    List<ChatMessage> findTop6BySessionIdAndFilteredFalseOrderBySequenceDesc(Long sessionId);

    // 안전 장치로 걸러지지 않은 그 역할의 메시지 (오래된 것부터). 알레르기 말을 상담 전체에서 다시 찾을 때 쓴다
    List<ChatMessage> findBySessionIdAndRoleAndFilteredFalseOrderBySequenceAsc(Long sessionId, ChatMessageRole role);
}
