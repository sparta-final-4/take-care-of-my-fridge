package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.ContextMessage;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatReplyGenerator {

    /**
     * 사용자 메시지를 바탕으로 상담 답변을 스트리밍(Flux) 형태로 생성한다.
     * 어떤 구현을 쓸지는 app.chat.reply-generator(stub / openai)로 고른다.
     *
     * @param memberId    회원 ID (로그용)
     * @param sessionId   세션 ID
     * @param userMessage 사용자 입력 메시지
     * @param history     이전 대화 (오래된 것부터, 최대 3턴). 없으면 빈 목록
     * @return AI 상담 답변 문자열 스트림
     */
    Flux<String> generate(Long memberId, Long sessionId, String userMessage, List<ContextMessage> history);

    /**
     * 추천 답을 스트리밍으로 생성한다. 공통 시스템 프롬프트에 추천 규칙(prompts/chat-recommend.txt)을 더하고,
     * 후보 정보는 사용자 메시지 앞에 별도 메시지로 넣는다.
     *
     * @param memberId      회원 ID (로그용)
     * @param sessionId     세션 ID
     * @param userMessage   사용자 입력 메시지
     * @param candidateInfo 사용자 요청·추천 후보 줄 글 (RecommendationPromptBuilder)
     * @return 추천 설명 문자열 스트림
     */
    Flux<String> generateRecommendation(Long memberId, Long sessionId, String userMessage, String candidateInfo);

    /**
     * 추천한 레시피 하나에 대한 질문의 답을 스트리밍으로 생성한다. 공통 시스템 프롬프트에 레시피 답 규칙(prompts/chat-recipe-detail.txt)을 더하고,
     * 레시피 정보는 사용자 메시지 앞에 별도 메시지로 넣는다. 레시피 정보에 없는 내용은 지어내지 않게 한다.
     *
     * @param recipeInfo [레시피 정보] 줄 글 (RecipeDetailPromptBuilder)
     * @return 답 문자열 스트림
     */
    Flux<String> generateRecipeDetail(Long memberId, Long sessionId, String userMessage, String recipeInfo);
}
