import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Checkbox, Empty, Flex, Popconfirm, Result, Skeleton } from 'antd';
import { useState } from 'react';

import { changeCartItemQuantity, removeCartItems } from '@/domains/cart/api';
import { cartQueries } from '@/domains/cart/queries';

import { CartItemRow } from './CartItemRow';
import { CartSummary } from './CartSummary';
import styles from './CartSection.module.scss';

// 목록과 결제 예정 금액이 같은 체크 상태를 써서 한 섹션에 둔다.
export function CartSection() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { data: items, isPending, isError, refetch } = useQuery(cartQueries.items());
  // 체크를 해제한 줄만 기록한다. 새로 담은 줄과 품절이 풀린 줄은 처음부터 체크된다.
  const [uncheckedIds, setUncheckedIds] = useState<number[]>([]);
  const [pendingIds, setPendingIds] = useState<number[]>([]);

  const invalidateItems = () => queryClient.invalidateQueries({ queryKey: cartQueries.all });
  const changeQuantityMutation = useMutation({
    mutationFn: ({ cartItemId, quantity }: { cartItemId: number; quantity: number }) =>
      changeCartItemQuantity(cartItemId, { quantity }),
    onSuccess: invalidateItems,
  });
  const removeMutation = useMutation({
    mutationFn: removeCartItems,
    onSuccess: invalidateItems,
  });

  if (isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (isError) {
    return (
      <Result
        status="error"
        title="장바구니를 불러오지 못했습니다."
        extra={<Button onClick={() => refetch()}>다시 시도</Button>}
      />
    );
  }

  const orderableItems = items.filter((item) => !item.soldOut);
  const checkedItems = orderableItems.filter((item) => !uncheckedIds.includes(item.cartItemId));
  const allChecked = orderableItems.length > 0 && checkedItems.length === orderableItems.length;

  // 여러 줄을 연달아 바꿀 수 있어 진행 중인 줄을 모두 기록한다.
  // 실패 알림은 MutationCache.onError가 서버 메시지로 띄우고, 수량은 다시 불러온 값을 따른다.
  const runOnRows = async (cartItemIds: number[], request: () => Promise<unknown>) => {
    setPendingIds((ids) => [...ids, ...cartItemIds]);
    try {
      await request();
    } catch {
      // 실패 알림은 MutationCache.onError가 띄운다.
    } finally {
      setPendingIds((ids) => ids.filter((id) => !cartItemIds.includes(id)));
    }
  };

  const handleCheck = (cartItemId: number, checked: boolean) => {
    setUncheckedIds((ids) =>
      checked ? ids.filter((id) => id !== cartItemId) : [...ids, cartItemId],
    );
  };

  const handleCheckAll = (checked: boolean) => {
    setUncheckedIds(checked ? [] : orderableItems.map((item) => item.cartItemId));
  };

  const handleChangeQuantity = (cartItemId: number, quantity: number) =>
    runOnRows([cartItemId], () => changeQuantityMutation.mutateAsync({ cartItemId, quantity }));

  // 성공 메시지는 호출 옵션에 둔다. 화면을 벗어나면 실행되지 않는다.
  const handleRemove = (cartItemIds: number[]) =>
    runOnRows(cartItemIds, () =>
      removeMutation.mutateAsync(cartItemIds, {
        onSuccess: () => void message.success('삭제했습니다.'),
      }),
    );

  if (items.length === 0) {
    return <Empty description="장바구니에 담은 상품이 없습니다." className={styles.empty} />;
  }

  return (
    <div className={styles.layout}>
      <section className={styles.list} aria-label="장바구니 목록">
        <Flex align="center" justify="space-between" wrap gap="small" className={styles.toolbar}>
          <Checkbox
            checked={allChecked}
            indeterminate={checkedItems.length > 0 && !allChecked}
            disabled={orderableItems.length === 0}
            onChange={(event) => handleCheckAll(event.target.checked)}
          >
            전체 선택 ({checkedItems.length}/{items.length})
          </Checkbox>
          <Flex gap="small">
            <Popconfirm
              title="선택한 상품을 삭제할까요?"
              okText="삭제"
              cancelText="취소"
              okButtonProps={{ danger: true }}
              disabled={checkedItems.length === 0}
              onConfirm={() => handleRemove(checkedItems.map((item) => item.cartItemId))}
            >
              <Button danger disabled={checkedItems.length === 0}>
                선택 삭제
              </Button>
            </Popconfirm>
            <Popconfirm
              title="장바구니를 모두 비울까요?"
              okText="삭제"
              cancelText="취소"
              okButtonProps={{ danger: true }}
              onConfirm={() => handleRemove(items.map((item) => item.cartItemId))}
            >
              <Button danger>전체 삭제</Button>
            </Popconfirm>
          </Flex>
        </Flex>
        {items.map((item) => (
          <CartItemRow
            key={item.cartItemId}
            item={item}
            checked={!item.soldOut && !uncheckedIds.includes(item.cartItemId)}
            pending={pendingIds.includes(item.cartItemId)}
            onCheck={(checked) => handleCheck(item.cartItemId, checked)}
            onChangeQuantity={(quantity) => handleChangeQuantity(item.cartItemId, quantity)}
            onRemove={() => handleRemove([item.cartItemId])}
          />
        ))}
      </section>
      <div className={styles.summary}>
        <CartSummary checkedItems={checkedItems} />
      </div>
    </div>
  );
}
