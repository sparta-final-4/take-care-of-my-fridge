package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 상담 메시지를 어떻게 처리할지.
 * AUTO: 메시지를 AI가 읽어 추천 요청인지 일반 질문인지 판단한다 (직접 입력).
 * RECOMMEND: 판단 없이 추천으로 처리한다 ([알아서 추천해줘]·[이 재료로 추천받기] 칩).
 */
public enum ChatMessageMode {
    AUTO,
    RECOMMEND
}
