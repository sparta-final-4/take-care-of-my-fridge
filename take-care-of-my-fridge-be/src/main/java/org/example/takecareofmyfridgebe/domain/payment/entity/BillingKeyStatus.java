package org.example.takecareofmyfridgebe.domain.payment.entity;

// 등록 카드 상태. 삭제해도 행은 남겨 그 카드로 한 결제와 연결을 유지한다
public enum BillingKeyStatus {
    ACTIVE,  // 사용 중
    DELETED  // 삭제
}
