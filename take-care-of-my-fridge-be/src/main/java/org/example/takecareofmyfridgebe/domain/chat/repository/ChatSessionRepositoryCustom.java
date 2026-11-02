package org.example.takecareofmyfridgebe.domain.chat.repository;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListStatus;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionStatusCounts;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface ChatSessionRepositoryCustom {

    /**
     * @param activeSince 이 시각 이후 마지막 대화가 있어야 진행 중으로 본다(그 전이면 시간 초과로 만료)
     */
    Page<ChatSession> findMySessionsWithMessages(Long memberId, ChatSessionListStatus status,
                                                 LocalDateTime activeSince, Pageable pageable);

    ChatSessionStatusCounts countMySessionsWithMessages(Long memberId, LocalDateTime activeSince);
}
