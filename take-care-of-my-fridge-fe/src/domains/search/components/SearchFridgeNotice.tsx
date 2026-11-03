import { Alert, Button } from 'antd';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useSearchFridgeMatch } from '@/domains/search/hooks/useSearchFridgeMatch';

// 검색어가 든 재료가 내 냉장고에 있으면 결과 위에 알려 준다(로그인 회원만).
export function SearchFridgeNotice() {
  const { keywordItems } = useSearchFridgeMatch();
  if (keywordItems.length === 0) return null;

  return (
    <Alert
      type="info"
      showIcon
      title={`냉장고에 있어요: ${keywordItems.join(', ')}`}
      action={
        <Link to={ROUTES.fridge}>
          <Button size="small">내 냉장고</Button>
        </Link>
      }
    />
  );
}
