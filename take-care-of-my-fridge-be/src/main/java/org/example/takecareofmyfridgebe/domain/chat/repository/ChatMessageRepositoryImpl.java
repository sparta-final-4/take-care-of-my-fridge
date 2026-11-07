package org.example.takecareofmyfridgebe.domain.chat.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionMessageStats;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.entity.QChatMessage;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import static org.example.takecareofmyfridgebe.domain.chat.entity.QChatMessage.chatMessage;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 세션 ID 목록의 메시지 수, 첫 사용자 메시지(가장 작은 sequence의 USER 메시지),
     * 마지막 메시지(가장 큰 sequence, 역할 무관)를 한 번의 쿼리로 조회한다.
     * 메시지 수는 대화 화면에 보이는 수라 단어에 걸린 메시지(filtered)도 세고, 첫 질문·마지막 메시지는 걸린 메시지를 빼고 고른다
     * (걸린 메시지뿐이면 null).
     * 메시지가 없는 세션은 결과에 포함되지 않는다.
     */
    @Override
    public List<ChatSessionMessageStats> findStatsBySessionIds(Collection<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return List.of();
        }

        QChatMessage firstQuestion = new QChatMessage("firstQuestion");
        QChatMessage userMessage = new QChatMessage("userMessage");
        QChatMessage lastMessage = new QChatMessage("lastMessage");
        QChatMessage latest = new QChatMessage("latest");

        return queryFactory
                .select(Projections.constructor(
                        ChatSessionMessageStats.class,
                        chatMessage.session.id,
                        chatMessage.count(),
                        JPAExpressions.select(firstQuestion.content)
                                .from(firstQuestion)
                                .where(
                                        firstQuestion.session.id.eq(chatMessage.session.id),
                                        firstQuestion.sequence.eq(
                                                JPAExpressions.select(userMessage.sequence.min())
                                                        .from(userMessage)
                                                        .where(
                                                                userMessage.session.id.eq(chatMessage.session.id),
                                                                userMessage.role.eq(ChatMessageRole.USER),
                                                                userMessage.filtered.isFalse()
                                                        )
                                        )
                                ),
                        JPAExpressions.select(lastMessage.content)
                                .from(lastMessage)
                                .where(
                                        lastMessage.session.id.eq(chatMessage.session.id),
                                        lastMessage.sequence.eq(
                                                JPAExpressions.select(latest.sequence.max())
                                                        .from(latest)
                                                        .where(latest.session.id.eq(chatMessage.session.id), latest.filtered.isFalse())
                                        )
                                )
                ))
                .from(chatMessage)
                .where(chatMessage.session.id.in(sessionIds))
                .groupBy(chatMessage.session.id)
                .fetch();
    }
}
