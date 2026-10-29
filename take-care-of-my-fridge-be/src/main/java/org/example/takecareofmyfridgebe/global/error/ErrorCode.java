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
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "M009", "이미 사용 중인 전화번호입니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "M003", "현재 비밀번호가 일치하지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "M004", "현재 비밀번호와 다른 비밀번호를 입력해 주세요."),
    ALREADY_ADMIN(HttpStatus.CONFLICT, "M007", "이미 관리자인 회원입니다."),
    PROMOTION_SUSPENDED(HttpStatus.CONFLICT, "M006", "정지된 회원은 관리자로 승격할 수 없습니다."),

    // Forecast
    FORECAST_NOT_FOUND(HttpStatus.NOT_FOUND, "F001", "해당 주의 예측이 없습니다."),

    // Deal analysis
    DEAL_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "D002", "할인 후보 분석 결과가 없습니다."),
    DEAL_CANDIDATE_NOT_IN_ANALYSIS(HttpStatus.BAD_REQUEST, "D003", "이 분석의 할인 후보가 아닙니다."),
    DEAL_REVIEW_LOCKED(HttpStatus.CONFLICT, "D004", "이미 행사 요청한 후보는 검토 상태를 바꿀 수 없습니다."),
    DEAL_EVENT_NOT_AVAILABLE(HttpStatus.CONFLICT, "D005", "선택되지 않았거나 이미 행사에 등록된 후보입니다."),
    DEAL_SYNTHETIC_ANALYSIS(HttpStatus.CONFLICT, "D006", "가상 데이터 분석 결과로는 행사를 등록할 수 없습니다."),

    // Product
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "상품을 찾을 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "PRODUCT_002", "재고가 부족합니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "PRODUCT_003", "가격은 1원 이상이어야 합니다."),
    INVALID_STOCK(HttpStatus.BAD_REQUEST, "PRODUCT_004", "재고는 0 이상이어야 합니다."),
    PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "PRODUCT_005", "현재 판매하지 않는 상품입니다."),
    INVALID_PRODUCT_INGREDIENT(HttpStatus.BAD_REQUEST, "PRODUCT_006", "상품에 연결할 수 없는 재료입니다."),
    // 상품은 있는데 타입별 상세 행(ingredient_products, mealkit_products)이 없는 서버 데이터 불일치
    PRODUCT_DETAIL_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_007", "상품 상세 정보가 올바르지 않습니다."),

    // Cart
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_001", "장바구니 상품을 찾을 수 없습니다."),
    CART_QUANTITY_EXCEEDS_STOCK(HttpStatus.CONFLICT, "CART_002", "재고보다 많이 담을 수 없습니다."),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "CART_003", "구매 한도를 넘었습니다."),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_001", "주문을 찾을 수 없습니다."),
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "ORDER_002", "유효하지 않은 주문 상태 변경입니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORDER_003", "해당 주문에 접근할 권한이 없습니다."),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "ORDER_004", "결제대기 상태의 주문만 취소할 수 있습니다."),
    ORDER_NUMBER_CONFLICT(HttpStatus.CONFLICT, "ORDER_005", "잠시 후 다시 주문해 주세요."),
    ORDER_IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "ORDER_006", "다른 주문 요청에 이미 쓴 Idempotency-Key입니다."),
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "ORDER_007", "이미 결제가 완료된 주문입니다."),
    ORDER_CANCELLED(HttpStatus.CONFLICT, "ORDER_008", "취소된 주문입니다."),
    ORDER_NOT_PAID(HttpStatus.CONFLICT, "ORDER_009", "결제 완료된 주문만 구매 확정할 수 있습니다."),
    ORDER_PRICE_CHANGED(HttpStatus.CONFLICT, "ORDER_010", "가격이 바뀌었습니다."),
    ORDER_REFUND_IN_PROGRESS(HttpStatus.CONFLICT, "ORDER_011", "환불이 진행 중인 주문은 구매 확정할 수 없습니다."),
    ORDER_PAYMENT_ATTEMPTED(HttpStatus.CONFLICT, "ORDER_012", "결제를 시도한 주문은 취소할 수 없습니다."),

    // Sale event
    SALE_EVENT_PRODUCT_DUPLICATED(HttpStatus.CONFLICT, "SALE_001", "이미 행사가 있는 상품입니다."),
    SALE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SALE_002", "행사를 찾을 수 없습니다."),
    SALE_EVENT_NOT_EDITABLE(HttpStatus.CONFLICT, "SALE_003", "시작 전인 행사만 수정할 수 있습니다."),
    SALE_EVENT_NOT_ENDABLE(HttpStatus.CONFLICT, "SALE_004", "진행 중인 할인 행사만 종료할 수 있습니다."),
    SALE_EVENT_NOT_DELETABLE(HttpStatus.CONFLICT, "SALE_005", "시작 전인 할인 행사만 삭제할 수 있습니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_002", "결제 금액이 일치하지 않습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "PAYMENT_003", "유효하지 않은 결제 상태 변경입니다."),
    PAYMENT_NOT_PAID(HttpStatus.BAD_REQUEST, "PAYMENT_004", "PG사 결제가 완료되지 않았습니다."),
    ALREADY_PROCESSED_PAYMENT(HttpStatus.CONFLICT, "PAYMENT_005", "이미 처리된 결제입니다."),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT, "PAYMENT_006", "결제 가능 시간이 지나 결제가 취소되었습니다."),
    PAYMENT_ORDER_NOT_PAYABLE(HttpStatus.CONFLICT, "PAYMENT_007", "이미 결제됐거나 취소된 주문입니다."),
    PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "PAYMENT_008", "이미 진행 중인 결제가 있습니다. 잠시 후 다시 시도해 주세요."),
    PG_CANCEL_REJECTED(HttpStatus.BAD_GATEWAY, "PAYMENT_009", "PG사가 결제 취소를 거절했습니다."),

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
    INVALID_REFUND_STATE(HttpStatus.CONFLICT, "REFUND_009", "유효하지 않은 환불 상태 변경입니다."),
    REFUND_ORDER_CONFIRMED(HttpStatus.CONFLICT, "REFUND_010", "구매 확정된 주문은 환불할 수 없습니다."),
    NON_REFUNDABLE_SALE_ITEM(HttpStatus.BAD_REQUEST, "REFUND_011", "특가·번개 드롭 상품은 환불할 수 없습니다."),
    REFUND_PG_REJECTED(HttpStatus.CONFLICT, "REFUND_012", "결제사에서 환불이 거절되었습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_REFUND_SUMMARY_REQUEST(HttpStatus.BAD_REQUEST, "REFUND_013", "환불 요약은 주문 1~50건까지 조회할 수 있습니다."),

    // Chat
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "채팅방을 찾을 수 없습니다."),
    INVALID_CHAT_ROOM_STATUS(HttpStatus.BAD_REQUEST, "CHAT_002", "유효하지 않은 채팅방 상태 변경입니다."),
    ALREADY_ACTIVE_ROOM_EXISTS(HttpStatus.BAD_REQUEST, "CHAT_003", "이미 진행 중인 상담이 존재합니다."),
    CHAT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHAT_004", "해당 채팅방에 접근할 권한이 없습니다."),
    CHAT_ROOM_ALREADY_COMPLETED(HttpStatus.CONFLICT, "CHAT_005", "이미 종료된 상담입니다."),
    CHAT_SESSION_EXPIRED(HttpStatus.CONFLICT, "CHAT_006", "상담 세션이 만료되었습니다."),
    CHAT_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "CHAT_007", "하루 상담 질문 가능 횟수를 초과했습니다."),
    CHAT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_008", "일시적으로 상담 기능을 이용할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    // Recipe
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "RECIPE_001", "레시피를 찾을 수 없습니다."),

    // Review
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_001", "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW_002", "이미 후기를 작성한 레시피입니다."),
    REVIEW_NOT_COOKED(HttpStatus.FORBIDDEN, "REVIEW_003", "요리를 완료한 레시피만 후기를 쓸 수 있어요."),
    REVIEW_SUMMARY_RUNNING(HttpStatus.CONFLICT, "REVIEW_004", "후기 요약이 이미 실행 중이에요."),

    // Notification
    INVALID_NOTIFICATION(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION_001", "알림 정보가 올바르지 않습니다."),

    // Ingredient
    CUSTOM_INGREDIENT_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "INGREDIENT_001", "직접 입력용 기본 재료가 설정되어 있지 않습니다."),
    INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "INGREDIENT_002", "재료를 찾을 수 없습니다."),

    // Fridge
    FRIDGE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIDGE_001", "냉장고 재료를 찾을 수 없습니다."),
    INVALID_FRIDGE_QUANTITY(HttpStatus.BAD_REQUEST, "FRIDGE_002", "수량은 0보다 커야 합니다."),
    INVALID_FRIDGE_ORDER_ITEM(HttpStatus.INTERNAL_SERVER_ERROR, "FRIDGE_003", "입고 출처와 주문 상품 정보가 일치하지 않습니다."),
    INVALID_FRIDGE_LOG(HttpStatus.INTERNAL_SERVER_ERROR, "FRIDGE_004", "유효하지 않은 냉장고 재료 이력입니다."),

    // Cooking
    COOKING_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "COOKING_001", "요리 기록을 찾을 수 없습니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
