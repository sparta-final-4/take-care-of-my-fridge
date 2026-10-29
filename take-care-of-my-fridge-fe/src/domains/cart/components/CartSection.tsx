import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Checkbox, Empty, Flex, Popconfirm, Result, Skeleton } from 'antd';
import { useState } from 'react';

import { changeCartItemQuantity, removeCartItems, type CartItemResponse } from '@/domains/cart/api';
import { cartChangeMutationKey, cartQueries } from '@/domains/cart/queries';

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

  // 수량 변경·삭제는 서버 응답을 기다리지 않고 화면부터 바꾼다(실패하면 서버 값으로 되돌린다).
  // 주문·결제처럼 서버가 확정해야 하는 동작에는 쓰지 않는다.
  const updateItems = (update: (items: CartItemResponse[]) => CartItemResponse[]) =>
    queryClient.setQueryData(cartQueries.items().queryKey, (current) => current && update(current));
  const startChange = async (update: (items: CartItemResponse[]) => CartItemResponse[]) => {
    // 진행 중인 조회 결과가 바꾼 화면을 덮어쓰지 않게 멈춘다.
    await queryClient.cancelQueries({ queryKey: cartQueries.all });
    updateItems(update);
  };
  // 연달아 바꾼 변경이 모두 끝난 뒤에 한 번만 서버 값을 다시 읽는다.
  // 중간에 다시 읽으면 화면 수량이 이전 값으로 돌아갔다가 다시 바뀐다. 실패했을 때도 여기서 서버 값으로 되돌린다.
  const syncItems = () => {
    if (queryClient.isMutating({ mutationKey: cartChangeMutationKey }) > 1) return;
    return queryClient.invalidateQueries({ queryKey: cartQueries.all });
  };
  // 빠르게 여러 번 눌러도 요청이 누른 순서대로 처리되도록 한 줄로 보낸다. 마지막 수량이 서버에 남는다.
  const changeScope = { id: cartChangeMutationKey.join('/') };
  const changeQuantityMutation = useMutation({
    mutationKey: cartChangeMutationKey,
    scope: changeScope,
    mutationFn: ({ cartItemId, quantity }: { cartItemId: number; quantity: number }) =>
      changeCartItemQuantity(cartItemId, { quantity }),
    onMutate: ({ cartItemId, quantity }) =>
      startChange((current) =>
        current.map((item) => (item.cartItemId === cartItemId ? { ...item, quantity } : item)),
      ),
    onSettled: syncItems,
  });
  const removeMutation = useMutation({
    mutationKey: cartChangeMutationKey,
    scope: changeScope,
    mutationFn: removeCartItems,
    onMutate: (cartItemIds) =>
      startChange((current) => current.filter((item) => !cartItemIds.includes(item.cartItemId))),
    onSettled: syncItems,
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

  const handleCheck = (cartItemId: number, checked: boolean) => {
    setUncheckedIds((ids) =>
      checked ? ids.filter((id) => id !== cartItemId) : [...ids, cartItemId],
    );
  };

  const handleCheckAll = (checked: boolean) => {
    setUncheckedIds(checked ? [] : orderableItems.map((item) => item.cartItemId));
  };

  const handleChangeQuantity = (cartItemId: number, quantity: number) =>
    changeQuantityMutation.mutate({ cartItemId, quantity });

  // 성공 메시지는 호출 옵션에 둔다. 화면을 벗어나면 실행되지 않는다.
  const handleRemove = (cartItemIds: number[]) =>
    removeMutation.mutate(cartItemIds, {
      onSuccess: () => void message.success('삭제했습니다.'),
    });

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
