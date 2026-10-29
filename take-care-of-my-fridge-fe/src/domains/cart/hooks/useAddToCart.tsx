import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App } from 'antd';
import { useLocation, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { addCartItem } from '@/domains/cart/api';
import { cartQueries } from '@/domains/cart/queries';

// 상품 목록·상세가 같이 쓰는 장바구니 담기. 로그인하지 않았으면 로그인 후 지금 화면으로 돌아온다.
export function useAddToCart() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const status = useAuthStore((state) => state.status);
  const { mutate, isPending, variables } = useMutation({
    mutationFn: addCartItem,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cartQueries.all }),
  });

  const addToCart = (productId: number, quantity = 1) => {
    if (status !== 'authenticated') {
      const redirect = `${location.pathname}${location.search}`;
      navigate(`${ROUTES.login}?${new URLSearchParams({ redirect })}`);
      return;
    }
    mutate(
      { productId, quantity },
      { onSuccess: () => void message.success('장바구니에 담았습니다.') },
    );
  };

  // 카드마다 로딩을 표시할 수 있게 진행 중인 상품 ID를 함께 준다.
  return { addToCart, pendingProductId: isPending ? variables.productId : undefined };
}
