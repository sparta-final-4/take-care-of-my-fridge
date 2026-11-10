import { Button, Drawer, Flex, Typography } from 'antd';
import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { addSmoothScroll } from '@/common/scroll/smoothScroll';
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
  const contentRef = useRef<HTMLDivElement>(null);
  const stopSmoothScrollRef = useRef<() => void>(undefined);

  // 화면 스크롤과 같은 부드러운 스크롤을 서랍 본문에도 붙인다. 열린 동안만 붙이고 닫히면 뗀다.
  const handleAfterOpenChange = (opened: boolean) => {
    stopSmoothScrollRef.current?.();
    stopSmoothScrollRef.current = undefined;
    const content = contentRef.current;
    const wrapper = content?.parentElement;
    if (!opened || !content || !wrapper) return;
    stopSmoothScrollRef.current = addSmoothScroll(wrapper, content);
  };
  useEffect(() => () => stopSmoothScrollRef.current?.(), []);

  return (
    <Drawer
      title="장바구니"
      placement="right"
      size={CART_DRAWER_SIZE}
      open={open}
      onClose={onClose}
      afterOpenChange={handleAfterOpenChange}
      destroyOnHidden
      rootClassName={styles.root}
      classNames={{
        mask: styles.mask,
        header: styles.header,
        title: styles.title,
        section: styles.section,
        body: styles.body,
      }}
    >
      <div ref={contentRef}>
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
      </div>
    </Drawer>
  );
}
