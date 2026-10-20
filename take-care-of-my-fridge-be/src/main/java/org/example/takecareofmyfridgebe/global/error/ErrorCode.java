package org.example.takecareofmyfridgebe.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통 에러
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "적절하지 않은 요청 값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "서버 에러가 발생했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A003", "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_002", "권한이 없습니다."),
    ADMIN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "A004", "관리자 계정만 볼 수 있는 페이지입니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M005", "회원을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "M001", "이미 가입된 이메일입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A005", "로그인이 만료되었어요. 다시 로그인해 주세요."),
    SESSION_REVOKED_BY_SUSPENSION(HttpStatus.UNAUTHORIZED, "A006", "계정 이용이 제한되어 로그인이 해제되었습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "A001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "A002", "정지된 계정입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "M002", "이미 사용 중인 닉네임입니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "M003", "현재 비밀번호가 일치하지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "M004", "현재 비밀번호와 다른 비밀번호를 입력해 주세요."),
    ALREADY_ADMIN(HttpStatus.CONFLICT, "M007", "이미 관리자인 회원입니다."),
    PROMOTION_SUSPENDED(HttpStatus.CONFLICT, "M006", "정지된 회원은 관리자로 승격할 수 없습니다."),

    // Product
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "상품을 찾을 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "PRODUCT_002", "재고가 부족합니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "PRODUCT_003", "가격은 0 이상이어야 합니다."),
    INVALID_STOCK(HttpStatus.BAD_REQUEST, "PRODUCT_004", "재고는 0 이상이어야 합니다."),
    PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "PRODUCT_005", "현재 판매하지 않는 상품입니다."),

    // Cart
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_001", "장바구니 상품을 찾을 수 없습니다."),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_001", "주문을 찾을 수 없습니다."),
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "ORDER_002", "유효하지 않은 주문 상태 변경입니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORDER_003", "해당 주문에 접근할 권한이 없습니다."),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "ORDER_004", "결제대기 상태의 주문만 취소할 수 있습니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_002", "결제 금액이 일치하지 않습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "PAYMENT_003", "유효하지 않은 결제 상태 변경입니다."),
    PAYMENT_NOT_PAID(HttpStatus.BAD_REQUEST, "PAYMENT_004", "PG사 결제가 완료되지 않았습니다."),
    ALREADY_PROCESSED_PAYMENT(HttpStatus.CONFLICT, "PAYMENT_005", "이미 처리된 결제입니다."),

    // Webhook
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "WEBHOOK_001", "웹훅 서명이 유효하지 않습니다."),
    WEBHOOK_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "WEBHOOK_002", "웹훅 이벤트를 찾을 수 없습니다."),
    
    // Refund
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "REFUND_001", "존재하지 않는 환불 건입니다."),
    REFUND_ACCESS_DENIED(HttpStatus.FORBIDDEN, "REFUND_002", "본인의 결제 건만 환불할 수 있습니다."),
    INVALID_REFUND_STATUS(HttpStatus.BAD_REQUEST, "REFUND_003", "환불 가능한 결제 상태가 아닙니다."),
    REFUND_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "REFUND_004", "환불 대상 상품이 존재하지 않습니다."),
    EXCEED_REFUNDABLE_QUANTITY(HttpStatus.BAD_REQUEST, "REFUND_005", "잔여 환불 가능 수량을 초과했습니다."),
    REFUND_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "REFUND_006", "DB와 PG사의 결제 잔액이 일치하지 않습니다."),
    DUPLICATE_REFUND_REQUEST(HttpStatus.TOO_MANY_REQUESTS, "REFUND_007", "환불 처리가 진행 중입니다. 잠시 후 다시 시도해주세요."),
    INVALID_REFUND_QUANTITY(HttpStatus.BAD_REQUEST, "REFUND_008", "수량은 1 이상이어야 합니다."),

    // Chat
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "채팅방을 찾을 수 없습니다."),
    INVALID_CHAT_ROOM_STATUS(HttpStatus.BAD_REQUEST, "CHAT_002", "유효하지 않은 채팅방 상태 변경입니다."),
    ALREADY_ACTIVE_ROOM_EXISTS(HttpStatus.BAD_REQUEST, "CHAT_003", "이미 진행 중인 상담이 존재합니다."),
    CHAT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHAT_004", "해당 채팅방에 접근할 권한이 없습니다."),

    // Review
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_001", "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW_002", "이미 후기를 작성한 레시피입니다."),

    // Notification
    INVALID_NOTIFICATION(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION_001", "알림 정보가 올바르지 않습니다."),

    // Ingredient
    CUSTOM_INGREDIENT_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "INGREDIENT_001", "직접 입력용 기본 재료가 설정되어 있지 않습니다."),

    // Fridge
    FRIDGE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIDGE_001", "냉장고 재료를 찾을 수 없습니다."),
    INVALID_FRIDGE_QUANTITY(HttpStatus.BAD_REQUEST, "FRIDGE_002", "수량은 0보다 커야 합니다."),
    INVALID_FRIDGE_ORDER_ITEM(HttpStatus.INTERNAL_SERVER_ERROR, "FRIDGE_003", "입고 출처와 주문 상품 정보가 일치하지 않습니다."),
    INVALID_FRIDGE_LOG(HttpStatus.INTERNAL_SERVER_ERROR, "FRIDGE_004", "유효하지 않은 냉장고 재료 이력입니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
