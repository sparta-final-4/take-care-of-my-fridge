package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;

import java.util.List;

/**
 * 이번 메시지를 처리할 때 쓰는 상담 문맥: 최근 대화(최대 3턴)와 AI 답에 붙었던 카드 이름.
 * 이전 대화는 AI에 그대로 넘기므로, AI 답은 소개만 저장돼 있어도 보여 준 카드 이름을 함께 둔다.
 *
 * @param recentMessages 최근 메시지 (오래된 것부터, 최대 6개)
 */
public record ChatContext(
        List<ContextMessage> recentMessages
) {

    // AI 답 하나를 이전 대화로 넣을 때 최대 글자 수 (긴 레시피 자세히 답이 쌓이지 않게)
    public static final int ASSISTANT_MAX_LENGTH = 300;

    public ChatContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }

    public static ChatContext empty() {
        return new ChatContext(List.of());
    }

    /**
     * 이전 메시지 하나.
     *
     * @param cards AI 답에 붙었던 레시피 카드 (보여 준 순서, 지워진 레시피는 빠짐). 사용자 메시지면 빈 목록
     */
    public record ContextMessage(
            ChatMessageRole role,
            String content,
            List<CardName> cards
    ) {
        public ContextMessage {
            cards = cards == null ? List.of() : List.copyOf(cards);
        }

        /**
         * AI에 넘길 글. AI 답은 300자까지 자르고 보여 준 카드 이름을 붙인다.
         * 예: "김피탕 레시피는 없어서, …\n(보여 준 카드: 1. 탕수육 2. 깐풍기)"
         */
        public String promptText() {
            if (role != ChatMessageRole.ASSISTANT) {
                return content;
            }
            String text = content.length() > ASSISTANT_MAX_LENGTH
                    ? content.substring(0, ASSISTANT_MAX_LENGTH) + "…"
                    : content;
            if (cards.isEmpty()) {
                return text;
            }
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < cards.size(); i++) {
                names.append(i == 0 ? "" : " ").append(i + 1).append(". ").append(cards.get(i).name());
            }
            return text + "\n(보여 준 카드: " + names + ")";
        }
    }

    public record CardName(Long recipeId, String name) {
    }
}
