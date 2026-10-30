import { useQuery } from '@tanstack/react-query';
import { Skeleton } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { useAddToCart } from '@/domains/cart/hooks/useAddToCart';
import { productQueries } from '@/domains/product/queries';

import { ProductCard } from './ProductCard';
import styles from './TodayMealkitSection.module.scss';

const MEALKIT_COUNT = 3;

// 추천 기준이 아직 없어 최신 밀키트를 보여 준다. 불러오지 못하거나 밀키트가 없으면 섹션을 숨긴다.
export function TodayMealkitSection() {
  const { data, isPending } = useQuery(
    productQueries.list({ type: 'MEALKIT', sort: 'LATEST', page: 1, size: MEALKIT_COUNT }),
  );
  const { addToCart, pendingProductId } = useAddToCart();
  const mealkits = data?.content ?? [];

  if (!isPending && mealkits.length === 0) return null;

  return (
    <SectionCard title="오늘의 밀키트" bracket={false}>
      {isPending ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : (
        <ul className={styles.list} aria-label="오늘의 밀키트">
          {mealkits.map((product) => (
            <li key={product.productId} className={styles.item}>
              <ProductCard
                product={product}
                pending={pendingProductId === product.productId}
                onAddToCart={addToCart}
                wide
              />
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}
