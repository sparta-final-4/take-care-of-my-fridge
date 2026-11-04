package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 상담 메시지의 의도.
 * RECOMMEND: 메뉴·레시피 추천 요청, GENERAL: 그 밖의 질문(보관법·손질법 등)
 */
public enum ChatIntent {
    RECOMMEND,
    GENERAL
}
