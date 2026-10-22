import { useMutation } from '@tanstack/react-query';
import { App } from 'antd';
import { useRef, useState } from 'react';
import { useBlocker, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { cancelOrder } from '@/domains/order/api';

// 주문 확인 화면에서 앱 안 이동(뒤로 가기, 메뉴)을 붙잡고, 나가기를 고르면 주문을 취소한 뒤 보낸다.
// 탭 닫기·새로고침은 붙잡지 않는다. 그렇게 남은 주문은 서버의 주문 만료가 정리한다.
export function useCancelOnLeave(orderId: number, enabled: boolean) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  // 취소를 마친 뒤의 이동은 다시 붙잡지 않는다
  const leaveAllowedRef = useRef(false);
  // 취소하기 버튼으로 연 확인 창
  const [cancelAsked, setCancelAsked] = useState(false);
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      enabled && !leaveAllowedRef.current && currentLocation.pathname !== nextLocation.pathname,
  );
  const cancelMutation = useMutation({
    mutationFn: () => cancelOrder(orderId),
    onSuccess: () => {
      leaveAllowedRef.current = true;
      message.success('주문이 취소되었습니다.');
    },
  });

  const isLeaving = blocker.state === 'blocked';

  // 확인 창에서 취소를 고르면 부른다.
  // 이동을 붙잡은 경우에는 가려던 곳으로, 취소하기 버튼으로 연 경우에는 장바구니로 보낸다(장바구니는 그대로 남아 있다).
  // 취소에 실패하면 이동을 멈추고 화면에 남는다.
  const confirm = () =>
    cancelMutation.mutate(undefined, {
      onSuccess: () => (isLeaving ? blocker.proceed?.() : navigate(ROUTES.cart)),
      onError: () => {
        setCancelAsked(false);
        blocker.reset?.();
      },
    });

  const close = () => {
    setCancelAsked(false);
    blocker.reset?.();
  };

  return {
    isLeaving,
    isConfirmOpen: isLeaving || cancelAsked,
    isCancelling: cancelMutation.isPending,
    askCancel: () => setCancelAsked(true),
    confirm,
    close,
    // 결제를 마친 뒤처럼 주문을 취소하지 않고 떠나는 이동 직전에 부른다
    allowLeave: () => {
      leaveAllowedRef.current = true;
    },
  };
}
