import { MinusOutlined, PlusOutlined, DownOutlined, UpOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Flex,
  Input,
  InputNumber,
  Modal,
  Radio,
  Result,
  Spin,
  Typography,
} from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { CookingCompleteRequestResultEnum, IngredientItem } from '@/common/api/clients';
import { getErrorMessage } from '@/common/api/errors';
import { useAuthStore } from '@/common/auth/authStore';
import type { FridgeItemResponse } from '@/domains/fridge/api';
import { getStep } from '@/domains/fridge/format';
import { useCompleteCooking } from '@/domains/cooking/queries';
import { fridgeQueries } from '@/domains/fridge/queries';

import styles from './CookingCompleteModal.module.scss';

export interface CookingCompleteModalProps {
  open: boolean;
  onClose: () => void;
  recipeId: number;
  recipeName: string;
  ingredients: IngredientItem[];
  chatSessionId?: number;
}

interface HeldItemState {
  ingredientId: number;
  displayName: string;
  baseUnit: string;
  totalFridgeQty: number;
  expiredFridgeQty: number;
  checked: boolean;
  quantity: number | null;
  isAll: boolean;
}

interface MissingItem {
  ingredientId: number;
  displayName: string;
}

interface ModalBodyProps {
  recipeId: number;
  ingredients: IngredientItem[];
  fridgeItems: FridgeItemResponse[];
  chatSessionId?: number;
  onClose: () => void;
}

function CookingCompleteModalBody({
  recipeId,
  ingredients,
  fridgeItems,
  chatSessionId,
  onClose,
}: ModalBodyProps) {
  const navigate = useNavigate();
  const { message, modal } = App.useApp();
  const completeCookingMutation = useCompleteCooking();

  // 1. 레시피 재료 병합 (basicSeasoning 제외 & 같은 ingredientId 합치기)
  const mergedRecipeIngredients = useMemo(() => {
    const map = new Map<number, { displayName: string; quantity: number | null }>();

    ingredients.forEach((ing) => {
      // 기본 양념(basicSeasoning=true)은 제외
      if (ing.basicSeasoning || ing.ingredientId == null) return;

      const existing = map.get(ing.ingredientId);
      if (!existing) {
        map.set(ing.ingredientId, {
          displayName: ing.displayName ?? '재료',
          quantity: ing.quantity ?? null,
        });
      } else {
        // 이름은 첫 줄 displayName 유지
        // quantity는 모두 숫자면 합산, 하나라도 null이면 null
        const newQty =
          existing.quantity != null && ing.quantity != null
            ? Math.round((existing.quantity + ing.quantity) * 100) / 100
            : null;
        map.set(ing.ingredientId, {
          displayName: existing.displayName,
          quantity: newQty,
        });
      }
    });

    return map;
  }, [ingredients]);

  // 보유/미보유 항목 초기 분류
  const { initialHeld, missingItems } = useMemo(() => {
    const held: Record<number, HeldItemState> = {};
    const missing: MissingItem[] = [];

    mergedRecipeIngredients.forEach(({ displayName, quantity }, ingredientId) => {
      const matched = fridgeItems.filter((item) => item.ingredientId === ingredientId);

      if (matched.length > 0) {
        // 소비기한 지난 재료까지 포함한 총 수량
        const totalFridgeQty = matched.reduce((acc, cur) => acc + (cur.quantity ?? 0), 0);
        // 소비기한 지난 재료 수량
        const expiredFridgeQty = matched
          .filter((item) => item.status === 'EXPIRED' || item.expired)
          .reduce((acc, cur) => acc + (cur.quantity ?? 0), 0);
        const baseUnit = matched[0]?.baseUnit ?? '개';

        held[ingredientId] = {
          ingredientId,
          displayName,
          baseUnit,
          totalFridgeQty: Math.round(totalFridgeQty * 100) / 100,
          expiredFridgeQty: Math.round(expiredFridgeQty * 100) / 100,
          checked: true,
          quantity: quantity != null ? quantity : null,
          isAll: false,
        };
      } else {
        missing.push({ ingredientId, displayName });
      }
    });

    return { initialHeld: held, missingItems: missing };
  }, [mergedRecipeIngredients, fridgeItems]);

  const [heldStates, setHeldStates] = useState<Record<number, HeldItemState>>(initialHeld);
  const [missingExpanded, setMissingExpanded] = useState(false);
  const [result, setResult] = useState<CookingCompleteRequestResultEnum | null>(null);
  const [memo, setMemo] = useState('');

  const heldList = useMemo(() => Object.values(heldStates), [heldStates]);
  const checkedItems = useMemo(() => heldList.filter((item) => item.checked), [heldList]);

  // 체크된 재료 중 수량이 빈 칸이고 [전부]도 아닌 항목이 있는지 검사
  const hasEmptyQuantity = useMemo(
    () =>
      checkedItems.some(
        (item) =>
          !item.isAll && (item.quantity == null || isNaN(item.quantity) || item.quantity <= 0),
      ),
    [checkedItems],
  );

  // 핸들러: 체크박스 토글
  const handleToggleCheck = (ingredientId: number, checked: boolean) => {
    setHeldStates((prev) => {
      const current = prev[ingredientId];
      if (!current) return prev;
      return {
        ...prev,
        [ingredientId]: { ...current, checked },
      };
    });
  };

  // 핸들러: 수량 직접 입력
  const handleQuantityChange = (ingredientId: number, value: number | null) => {
    setHeldStates((prev) => {
      const current = prev[ingredientId];
      if (!current) return prev;
      return {
        ...prev,
        [ingredientId]: { ...current, quantity: value, isAll: false },
      };
    });
  };

  // 핸들러: 증감 버튼
  const handleStepChange = (ingredientId: number, delta: number) => {
    setHeldStates((prev) => {
      const current = prev[ingredientId];
      if (!current) return prev;
      const step = getStep(current.baseUnit);
      const curVal = current.quantity ?? 0;
      let nextVal = Math.round((curVal + delta * step) * 100) / 100;
      if (nextVal <= 0) {
        nextVal = step;
      }
      return {
        ...prev,
        [ingredientId]: { ...current, quantity: nextVal, isAll: false },
      };
    });
  };

  // 핸들러: [전부] 버튼 토글
  const handleToggleAll = (ingredientId: number) => {
    setHeldStates((prev) => {
      const current = prev[ingredientId];
      if (!current) return prev;
      const nextIsAll = !current.isAll;
      return {
        ...prev,
        [ingredientId]: { ...current, isAll: nextIsAll },
      };
    });
  };

  // 상단 요약 안내 문구
  const summaryText = useMemo(() => {
    if (checkedItems.length === 0) {
      return '냉장고에서 빠지는 재료가 없어요';
    }
    const parts = checkedItems.map((item) => {
      if (item.isAll) {
        return `${item.displayName} 전부`;
      }
      return `${item.displayName} ${item.quantity ?? 0}${item.baseUnit}`;
    });
    return `${parts.join(', ')}가 냉장고에서 빠져요`;
  }, [checkedItems]);

  const canSubmit = result !== null && !hasEmptyQuantity && !completeCookingMutation.isPending;

  // 요리 완료 제출
  const handleSubmit = () => {
    if (!canSubmit || result === null) return;

    const usedIngredients = checkedItems.map((item) => ({
      ingredientId: item.ingredientId,
      // [전부] 선택 시 명시적 null 전송 (서버에서 전부 차감 처리)
      quantity: (item.isAll ? null : (item.quantity ?? null)) as unknown as number,
    }));

    completeCookingMutation.mutate(
      {
        recipeId,
        result,
        memo: memo.trim() || undefined,
        chatSessionId,
        usedIngredients,
      },
      {
        onSuccess: (res) => {
          onClose();

          // 차감 결과 안내
          const deductions = res.deductions?.filter((d) => (d.deductedQuantity ?? 0) > 0) ?? [];
          if (deductions.length > 0) {
            const deductionMsg = deductions
              .map((d) => {
                const held = heldStates[d.ingredientId ?? 0];
                const name = held?.displayName ?? `재료(${d.ingredientId})`;
                const unit = held?.baseUnit ?? '';
                return `${name} ${d.deductedQuantity}${unit}`;
              })
              .join(', ');
            message.success(`요리 완료! ${deductionMsg}를 냉장고에서 뺐어요`);
          } else {
            message.success('요리 완료!');
          }

          // 요리 기록 확인 모달 (방안 A: 마이페이지 내 레시피 목록으로 이동하여 드롭다운 자동 펼침)
          modal.confirm({
            title: '요리 기록을 확인할까요?',
            content: '마이페이지에서 내 요리 기록을 바로 확인할 수 있어요.',
            okText: '기록 보러가기',
            cancelText: '레시피 계속 보기',
            onOk: () => {
              navigate(ROUTES.mypageRecipes, { state: { expandedRecipeId: recipeId } });
            },
          });
        },
        onError: (err) => {
          message.error(getErrorMessage(err) || '요리 완료 처리에 실패했습니다.');
        },
      },
    );
  };

  return (
    <>
      <div className={styles.modalContent}>
        {/* 1. 재료 칩 목록 */}
        <div className={styles.section}>
          <Typography.Text strong className={styles.sectionTitle}>
            냉장고 재료 차감
          </Typography.Text>

          {heldList.length === 0 ? (
            <Typography.Text type="secondary">
              냉장고에 보유 중인 레시피 재료가 없습니다.
            </Typography.Text>
          ) : (
            <div className={styles.ingredientList}>
              {heldList.map((item) => {
                const isExceed =
                  !item.isAll && item.quantity != null && item.quantity > item.totalFridgeQty;
                const isEmpty = !item.isAll && (item.quantity == null || item.quantity <= 0);

                return (
                  <div
                    key={item.ingredientId}
                    className={`${styles.ingredientCard} ${!item.checked ? styles.unchecked : ''}`}
                  >
                    <div className={styles.ingredientHeader}>
                      <Checkbox
                        checked={item.checked}
                        onChange={(e) => handleToggleCheck(item.ingredientId, e.target.checked)}
                      >
                        <span className={styles.ingredientName}>{item.displayName}</span>
                      </Checkbox>

                      <div className={styles.heldInfo}>
                        <span>
                          보유 {item.totalFridgeQty}
                          {item.baseUnit}
                        </span>
                        {item.expiredFridgeQty > 0 && (
                          <span className={styles.expiredWarning}>
                            소비기한 지난 {item.expiredFridgeQty}
                            {item.baseUnit} 포함
                          </span>
                        )}
                      </div>
                    </div>

                    {item.checked && (
                      <div className={styles.quantityControls}>
                        <Button
                          size="small"
                          type={item.isAll ? 'primary' : 'default'}
                          onClick={() => handleToggleAll(item.ingredientId)}
                          className={styles.allTag}
                        >
                          전부
                        </Button>

                        {!item.isAll && (
                          <div className={styles.stepperGroup}>
                            <Button
                              size="small"
                              icon={<MinusOutlined />}
                              onClick={() => handleStepChange(item.ingredientId, -1)}
                            />
                            <InputNumber
                              size="small"
                              className={styles.numberInput}
                              value={item.quantity}
                              min={0.01}
                              step={getStep(item.baseUnit)}
                              precision={2}
                              placeholder="수량"
                              onChange={(val) => handleQuantityChange(item.ingredientId, val)}
                            />
                            <Button
                              size="small"
                              icon={<PlusOutlined />}
                              onClick={() => handleStepChange(item.ingredientId, 1)}
                            />
                            <span className={styles.unitText}>{item.baseUnit}</span>
                          </div>
                        )}

                        {isExceed && <span className={styles.exceedNotice}>전부 빠져요</span>}
                        {isEmpty && (
                          <span className={styles.emptyNotice}>
                            수량을 입력하거나 [전부]를 눌러주세요
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* 냉장고에 없는 재료 (접힌 목록) */}
          {missingItems.length > 0 && (
            <div className={styles.missingSection}>
              <button
                type="button"
                className={styles.missingHeader}
                onClick={() => setMissingExpanded((prev) => !prev)}
              >
                <Typography.Text type="secondary">
                  냉장고에 없는 재료 {missingItems.length}개
                </Typography.Text>
                {missingExpanded ? <UpOutlined /> : <DownOutlined />}
              </button>
              {missingExpanded && (
                <div className={styles.missingList}>
                  {missingItems.map((m) => m.displayName).join(', ')}
                </div>
              )}
            </div>
          )}
        </div>

        {/* 2. 요리 결과 선택 (필수, 기본 선택 없음) */}
        <div className={styles.section}>
          <div className={styles.sectionTitle}>
            <span>요리 결과</span>
            <span className={styles.required}>*</span>
          </div>
          <Radio.Group
            value={result}
            onChange={(e) => setResult(e.target.value)}
            buttonStyle="solid"
          >
            <Radio.Button value="SUCCESS">성공</Radio.Button>
            <Radio.Button value="FAILED">실패</Radio.Button>
          </Radio.Group>
        </div>

        {/* 3. 메모 입력 (50자 제한) */}
        <div className={styles.section}>
          <Typography.Text strong className={styles.sectionTitle}>
            요리 메모
          </Typography.Text>
          <Input.TextArea
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            maxLength={50}
            showCount
            placeholder="요리 과정이나 맛에 대한 메모를 남겨보세요 (선택, 최대 50자)"
            rows={3}
          />
        </div>

        {/* 4. 버튼 위 요약 및 안내 */}
        <Alert
          type="info"
          showIcon
          className={styles.summaryAlert}
          title={summaryText}
          description="요리 기록은 삭제할 수 없어요."
        />
      </div>

      <div className={styles.footerActions}>
        <Button onClick={onClose} disabled={completeCookingMutation.isPending}>
          취소
        </Button>
        <Button
          type="primary"
          onClick={handleSubmit}
          loading={completeCookingMutation.isPending}
          disabled={!canSubmit}
        >
          요리 완료
        </Button>
      </div>
    </>
  );
}

export function CookingCompleteModal(props: CookingCompleteModalProps) {
  const { open, onClose, recipeId, recipeName, ingredients, chatSessionId } = props;
  const authenticated = useAuthStore((state) => state.status) === 'authenticated';

  // 냉장고 전체 아이템 조회 (소비기한 만료 항목 포함)
  const {
    data: fridgeItems,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    ...fridgeQueries.items('EXPIRATION'),
    enabled: authenticated && open,
  });

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={`${recipeName} 요리 완료`}
      width={520}
      footer={null}
      destroyOnHidden
    >
      {open && (
        <>
          {isLoading && (
            <Flex justify="center" align="center" style={{ minHeight: 200 }}>
              <Spin description="냉장고 재료를 불러오는 중..." />
            </Flex>
          )}

          {isError && (
            <Result
              status="error"
              title="냉장고 재료를 불러오지 못했어요"
              extra={
                <Button type="primary" onClick={() => void refetch()}>
                  다시 시도
                </Button>
              }
            />
          )}

          {!isLoading && !isError && fridgeItems && (
            <CookingCompleteModalBody
              key={recipeId}
              recipeId={recipeId}
              ingredients={ingredients}
              fridgeItems={fridgeItems}
              chatSessionId={chatSessionId}
              onClose={onClose}
            />
          )}
        </>
      )}
    </Modal>
  );
}
