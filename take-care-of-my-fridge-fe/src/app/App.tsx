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
      <AntdApp>
        <FeedbackBridge />
        <QueryClientProvider client={queryClient}>
          <IdleLogout />
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}
