import { Typography } from 'antd';
import { useRef } from 'react';
import { useLocation, useMatch, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';

import { ProductListSection } from '@/domains/product/components/ProductListSection';
import { ProductModal } from '@/domains/product/components/ProductModal';
import { ProductSearchSection } from '@/domains/product/components/ProductSearchSection';
import { TodayMealkitSection } from '@/domains/product/components/TodayMealkitSection';
import { isProductModalFromList } from '@/domains/product/productModal';

import styles from './ProductsPage.module.scss';

// 레퍼런스(row7seeds.com 상품 목록)의 구성: 제목·한 줄 설명 →
// 왼쪽 필터 칸 + 오른쪽 칸(오늘의 밀키트, 그 아래 상품 목록과 위에 붙는 도구 줄: 필터·개수·정렬).
// 좁은 화면은 필터를 서랍으로 연다.
// 상품 주소(/products/:productId)는 어디서 들어와도(카드·공유 링크·장바구니·로그인 후 복귀) 이 목록 위에 상품 모달을 띄운다.
export default function ProductsPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const detailMatch = useMatch(ROUTES.productDetail);
  const productId = detailMatch ? Number(detailMatch.params.productId) : undefined;
  // 닫기를 빠르게 두 번 누르면 뒤로 가기가 두 번 되어 목록 앞 화면까지 가므로, 한 주소에서 한 번만 닫는다.
  const closedKeyRef = useRef<string | null>(null);

  const handleCloseModal = () => {
    if (closedKeyRef.current === location.key) return;
    closedKeyRef.current = location.key;
    // 목록에서 카드를 눌러 열었으면 뒤로 가기, 링크로 바로 들어왔으면 같은 조건의 목록 주소로 바꾼다.
    if (isProductModalFromList(location.state)) navigate(-1);
    else navigate({ pathname: ROUTES.products, search: location.search }, { replace: true });
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <Typography.Title level={2} className={styles.title}>
          재료/밀키트
        </Typography.Title>
        <Typography.Paragraph className={styles.lede}>
          냉장고에 모자란 재료와 바로 만들 밀키트를 골라 보세요.
        </Typography.Paragraph>
      </header>

      {/* 필터 칸은 오늘의 밀키트 옆부터 시작한다. 오늘의 밀키트와 상품 목록은 오른쪽 칸에 위아래로. */}
      <div className={styles.collection}>
        <aside className={styles.sidebar} aria-label="상품 필터">
          <ProductSearchSection />
        </aside>
        <div className={styles.main}>
          <TodayMealkitSection />
          <ProductListSection />
        </div>
      </div>

      <ProductModal productId={productId} onClose={handleCloseModal} />
    </div>
  );
}
