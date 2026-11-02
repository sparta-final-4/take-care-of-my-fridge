package org.example.takecareofmyfridgebe.domain.chat.dto;

// 상담 목록 상태 필터. ENDED는 종료(COMPLETED)와 만료(EXPIRED, 시간 초과로 아직 ACTIVE인 상담 포함)
public enum ChatSessionListStatus {
    ALL,
    ACTIVE,
    ENDED
}
