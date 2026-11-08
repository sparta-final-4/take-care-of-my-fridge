import { Button, Drawer, Flex, Typography } from 'antd';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { CartSection } from '@/domains/cart/components/CartSection';

import styles from './CartDrawer.module.scss';

// 레퍼런스(row7seeds.com)의 장바구니 서랍: 오른쪽에서 밀려 나온다.
// 폭은 서랍 테마(styles/_cart-theme.scss)가 CSS 변수로 정한다(기본 화면의 92%까지 최대 450px).
const CART_DRAWER_SIZE = 'var(--cart-drawer-width, min(92vw, 450px))';

interface CartDrawerProps {
  open: boolean;
  onClose: () => void;
}

// 헤더 장바구니 아이콘이 여는 서랍. 안의 목록·수량·삭제·주문은 장바구니 화면과 같은 CartSection을 그대로 쓴다.
export function CartDrawer({ open, onClose }: CartDrawerProps) {
  const navigate = useNavigate();
  const status = useAuthStore((state) => state.status);

  return (
    <Drawer
      title="장바구니"
      placement="right"
      size={CART_DRAWER_SIZE}
      open={open}
      onClose={onClose}
      destroyOnHidden
      rootClassName={styles.root}
      classNames={{
        header: styles.header,
        title: styles.title,
        section: styles.section,
        body: styles.body,
      }}
    >
      {status === 'authenticated' ? (
        <div className={styles.cart}>
          <CartSection />
        </div>
      ) : (
        <Flex vertical align="center" gap="middle" className={styles.signedOut}>
          <Typography.Text type="secondary">로그인하면 장바구니를 볼 수 있어요.</Typography.Text>
          <Button
            type="primary"
            onClick={() => {
              onClose();
              navigate(ROUTES.login);
            }}
          >
            로그인
          </Button>
        </Flex>
      )}
    </Drawer>
  );
}
