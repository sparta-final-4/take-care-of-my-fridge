package org.example.takecareofmyfridgebe.domain.fridge.dto;

import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

/**
 * 냉장고 재료 빼기 요청 사유.
 * DB(fridge_item_logs)에 그대로 저장되는 값이 아니라 API 요청 전용 값이어서 dto 패키지에 둔다.
 */
public enum FridgeRemoveReason {

    CONSUMED {
        // 다 먹어서 소진한 재료는 소비 이력 및 통계 집계를 위해 로그를 남겨야 한다.
        @Override
        public boolean requiresLog() {
            return true;
        }

        // 다 먹은 사유는 DB 이력 테이블의 CONSUMED 액션으로 기록한다.
        @Override
        public FridgeItemAction toAction() {
            return FridgeItemAction.CONSUMED;
        }
    },

    DISCARDED {
        // 소비기한 경과 등으로 버린 재료는 폐기 이력 및 통계 집계를 위해 로그를 남겨야 한다.
        @Override
        public boolean requiresLog() {
            return true;
        }

        // 버린 사유는 DB 이력 테이블의 DISCARDED 액션으로 기록한다.
        @Override
        public FridgeItemAction toAction() {
            return FridgeItemAction.DISCARDED;
        }
    },

    WRONG_ENTRY {
        // 잘못 등록해서 지우는 경우는 실제 소비·폐기가 아니므로 이력을 남기지 않는다.
        @Override
        public boolean requiresLog() {
            return false;
        }

        // 이력을 남기지 않는 사유이므로 DB 액션으로 변환하려고 하면 예외를 던져 잘못된 호출을 막는다.
        @Override
        public FridgeItemAction toAction() {
            throw new BusinessException(ErrorCode.INVALID_FRIDGE_LOG);
        }
    };

    public abstract boolean requiresLog();

    public abstract FridgeItemAction toAction();
}
