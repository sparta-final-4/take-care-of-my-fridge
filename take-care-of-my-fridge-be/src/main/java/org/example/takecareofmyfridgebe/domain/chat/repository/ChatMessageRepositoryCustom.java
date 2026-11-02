package org.example.takecareofmyfridgebe.domain.chat.repository;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionMessageStats;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRepositoryCustom {

    List<ChatSessionMessageStats> findStatsBySessionIds(Collection<Long> sessionIds);
}
