import { QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import koKR from 'antd/locale/ko_KR';
import { useEffect } from 'react';
import { RouterProvider } from 'react-router/dom';

import { IdleLogout } from '@/common/auth/IdleLogout';
import { initSession } from '@/common/auth/initialize';
import '@/common/auth/interceptors';

import { FeedbackBridge } from './FeedbackBridge';
import { queryClient } from './queryClient';
import { router } from './router';
import { theme } from './theme';

export function App() {
  useEffect(() => {
    void initSession();
  }, []);

  return (
    <ConfigProvider theme={theme} locale={koKR}>
      {/* 알림 목록이 화면 폭 전체를 덮어 알림이 떠 있는 동안 같은 높이의 헤더 버튼이 눌리지 않으므로,
          목록은 클릭을 통과시키고 알림 내용만 클릭을 받게 한다. */}
      <AntdApp
        message={{
          styles: { listContent: { pointerEvents: 'none' }, wrapper: { pointerEvents: 'auto' } },
        }}
      >
        <FeedbackBridge />
        <QueryClientProvider client={queryClient}>
          <IdleLogout />
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}
