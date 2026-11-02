package org.example.takecareofmyfridgebe.domain.chat.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListStatus;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionStatusCounts;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static org.example.takecareofmyfridgebe.domain.chat.entity.QChatMessage.chatMessage;
import static org.example.takecareofmyfridgebe.domain.chat.entity.QChatSession.chatSession;

@Repository
@RequiredArgsConstructor
public class ChatSessionRepositoryImpl implements ChatSessionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 회원의 상담 세션 중 메시지가 1개 이상인 세션을 상태 필터에 맞춰 최근 시작순으로 페이지 조회한다.
     * 전체 개수는 필요할 때만 별도 count 쿼리로 구한다.
     */
    @Override
    public Page<ChatSession> findMySessionsWithMessages(Long memberId, ChatSessionListStatus status,
                                                        LocalDateTime activeSince, Pageable pageable) {
        BooleanExpression statusFilter = switch (status) {
            case ALL -> null;
            case ACTIVE -> activeNow(activeSince);
            case ENDED -> activeNow(activeSince).not();
        };

        List<ChatSession> content = queryFactory
                .selectFrom(chatSession)
                .where(chatSession.memberId.eq(memberId), hasMessage(), statusFilter)
                .orderBy(chatSession.createdAt.desc(), chatSession.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(chatSession.count())
                .from(chatSession)
                .where(chatSession.memberId.eq(memberId), hasMessage(), statusFilter);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 메시지가 있는 회원 상담의 전체·진행 중 개수를 한 번에 구한다. 종료 = 전체 - 진행 중.
     */
    @Override
    public ChatSessionStatusCounts countMySessionsWithMessages(Long memberId, LocalDateTime activeSince) {
        NumberExpression<Long> total = chatSession.count();
        NumberExpression<Long> active = new CaseBuilder()
                .when(activeNow(activeSince)).then(1L)
                .otherwise(0L)
                .sum();

        Tuple row = queryFactory
                .select(total, active)
                .from(chatSession)
                .where(chatSession.memberId.eq(memberId), hasMessage())
                .fetchOne();

        long totalCount = row == null || row.get(total) == null ? 0 : row.get(total);
        long activeCount = row == null || row.get(active) == null ? 0 : row.get(active);
        return new ChatSessionStatusCounts(totalCount, activeCount, totalCount - activeCount);
    }

    // ACTIVE이면서 시간 초과 전인 상담. 시간이 지난 ACTIVE는 다음 상담 시작 때 EXPIRED가 되므로 만료로 본다.
    private BooleanExpression activeNow(LocalDateTime activeSince) {
        return chatSession.status.eq(ChatSessionStatus.ACTIVE)
                .and(chatSession.lastMessageAt.goe(activeSince));
    }

    private BooleanExpression hasMessage() {
        return JPAExpressions.selectOne()
                .from(chatMessage)
                .where(chatMessage.session.id.eq(chatSession.id))
                .exists();
    }
}
