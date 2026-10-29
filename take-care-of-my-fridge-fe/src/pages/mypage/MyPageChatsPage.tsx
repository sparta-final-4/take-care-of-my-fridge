import { Button, Empty } from 'antd';

import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';

export default function MyPageChatsPage() {
  const openChat = useChatModalStore((state) => state.openChat);

  return (
    <Empty description="AI 상담 내역 목록 기능은 준비 중입니다.">
      <Button type="primary" onClick={openChat}>
        AI 상담 바로가기
      </Button>
    </Empty>
  );
}
