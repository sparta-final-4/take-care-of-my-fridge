import { Layout } from 'antd';
import { Outlet } from 'react-router';

import { ChatModal } from '@/domains/chat/components/ChatModal';

import { Header } from './Header';
import styles from './MainLayout.module.scss';

export function MainLayout() {
  return (
    <Layout className={styles.layout}>
      <Header />
      <Layout.Content className={styles.content}>
        <Outlet />
      </Layout.Content>
      <ChatModal />
    </Layout>
  );
}
