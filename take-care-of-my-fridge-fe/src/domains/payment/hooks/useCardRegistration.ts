import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App } from 'antd';
import { useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router';

import type { BillingKeyResponse } from '@/common/api/clients';
import { prepareCardRegistration, registerCard } from '@/domains/payment/api';
import { openCardRegistrationWindow } from '@/domains/payment/portone';
import { billingKeyQueries } from '@/domains/payment/queries';

type RegistrationResult = { card: BillingKeyResponse } | { card: null; message?: string };

const FAIL_MESSAGE = '카드 등록을 완료하지 못했어요.';
// 카드 등록 리다이렉트가 주소에 붙이는 결과 값. 이 값만 지우고 다른 쿼리는 그대로 둔다
const REDIRECT_RESULT_KEYS = [
  'transactionType',
  'billingKey',
  'code',
  'message',
  'pgCode',
  'pgMessage',
];
const ISSUE_BILLING_KEY = 'ISSUE_BILLING_KEY';

// 카드 등록 창을 열고, 창이 성공으로 끝나면 받은 빌링키를 서버에 넘긴다. 창을 닫거나 결제대행사가 실패하면 서버를 부르지 않는다.
// 모바일은 창 대신 결제대행사 페이지로 이동했다가 redirectPath로 돌아오며, 그때는 결과가 주소의 쿼리로 온다.
// 돌아온 주소의 결과는 한 번만 처리하고 주소에서 지운다. 등록되면 onRegistered로 새 카드를 알린다.
export function useCardRegistration(
  redirectPath: string,
  onRegistered?: (card: BillingKeyResponse) => void,
) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();

  // 성공·실패와 관계없이 서버의 카드 목록으로 맞춘다. 등록 확정이 실패해도 웹훅이 저장했을 수 있다.
  const refreshCards = () => queryClient.invalidateQueries({ queryKey: billingKeyQueries.all });
  const windowMutation = useMutation({
    mutationFn: async (): Promise<RegistrationResult> => {
      const prepared = await prepareCardRegistration();
      const result = await openCardRegistrationWindow(
        prepared,
        `${window.location.origin}${redirectPath}`,
      );
      // 창을 닫았거나 결제대행사가 실패했다. 메시지가 없어도 실패를 알린다(리다이렉트로 넘어간 경우는 결과가 없다)
      if (!result) {
        return { card: null };
      }
      if (result.code !== undefined || !result.billingKey) {
        return { card: null, message: result.message || FAIL_MESSAGE };
      }
      return { card: await registerCard(result.billingKey) };
    },
    onSettled: refreshCards,
  });
  const redirectMutation = useMutation({ mutationFn: registerCard, onSettled: refreshCards });

  const handleRegistered = (card: BillingKeyResponse) => {
    message.success('카드를 등록했어요.');
    onRegistered?.(card);
  };

  const redirectHandled = useRef(false);
  useEffect(() => {
    // 카드 등록 결과(transactionType=ISSUE_BILLING_KEY)일 때만 처리한다. 다른 흐름의 쿼리는 건드리지 않는다
    if (redirectHandled.current || searchParams.get('transactionType') !== ISSUE_BILLING_KEY) {
      return;
    }
    redirectHandled.current = true;
    const billingKey = searchParams.get('billingKey');
    const code = searchParams.get('code');
    const failMessage = searchParams.get('message');
    const rest = new URLSearchParams(searchParams);
    REDIRECT_RESULT_KEYS.forEach((key) => rest.delete(key));
    setSearchParams(rest, { replace: true });
    if (code || !billingKey) {
      message.warning(failMessage || FAIL_MESSAGE);
      return;
    }
    redirectMutation.mutate(billingKey, {
      onSuccess: (card) => {
        message.success('카드를 등록했어요.');
        onRegistered?.(card);
      },
    });
    // 의존 값이 바뀌어 다시 실행돼도 redirectHandled로 한 번만 처리한다
  }, [searchParams, setSearchParams, message, redirectMutation, onRegistered]);

  const register = () =>
    windowMutation.mutate(undefined, {
      onSuccess: (result) => {
        if (result.card) {
          handleRegistered(result.card);
        } else if (result.message) {
          message.warning(result.message);
        }
      },
    });

  return { register, registering: windowMutation.isPending || redirectMutation.isPending };
}
