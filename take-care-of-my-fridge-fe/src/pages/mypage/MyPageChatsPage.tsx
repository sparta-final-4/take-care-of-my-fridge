import { Button, Empty, Typography } from 'antd';

import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';

export default function MyPageChatsPage() {
  const openChat = useChatModalStore((state) => state.openChat);

  return (
    <>
      {/* 마이페이지 화면 제목(Title level 3, 24px / 600) */}
      <Typography.Title level={3}>AI 상담 내역</Typography.Title>
      <Empty description="AI 상담 내역 목록 기능은 준비 중입니다.">
        <Button type="primary" onClick={openChat}>
          AI 상담 바로가기
        </Button>
      </Empty>
    </>
  );
}
