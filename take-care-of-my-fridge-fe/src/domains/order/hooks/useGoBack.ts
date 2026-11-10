import { useLocation, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';

// 직전 화면으로 돌아간다. 이 화면이 처음 연 주소(주소 직접 입력, 새 탭)면 돌아갈 곳이 없어 상품 목록으로 보낸다.
export function useGoBack() {
  const navigate = useNavigate();
  const location = useLocation();
  return () => (location.key === 'default' ? navigate(ROUTES.products) : navigate(-1));
}
