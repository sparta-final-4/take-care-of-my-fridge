import { Button, Empty } from 'antd';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';

export default function MyPageChatsPage() {
  const navigate = useNavigate();

  return (
    <Empty description="AI 상담 내역 목록 기능은 준비 중입니다.">
      <Button type="primary" onClick={() => navigate(ROUTES.chat)}>
        AI 상담 바로가기
      </Button>
    </Empty>
  );
}
