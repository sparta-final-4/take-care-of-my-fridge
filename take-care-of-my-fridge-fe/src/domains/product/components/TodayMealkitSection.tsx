import { useQuery } from '@tanstack/react-query';
import { Flex, Skeleton, Typography } from 'antd';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';
import { productQueries } from '@/domains/product/queries';

import { ProductCard } from './ProductCard';
import styles from './TodayMealkitSection.module.scss';

const MEALKIT_COUNT = 3;
const MEALKIT_LIST_SEARCH = '?type=MEALKIT';

// 메인 화면 오늘의 밀키트(지금 뜨는 할인 아래). 추천 기준이 아직 없어 최신 밀키트를 보여 준다.
// 불러오지 못하거나 밀키트가 없으면 섹션을 숨긴다. 카드를 누르면 밀키트 목록 위에 상품 모달이 뜬다.
export function TodayMealkitSection() {
  const { data, isPending } = useQuery(
    productQueries.list({ type: 'MEALKIT', sort: 'LATEST', page: 1, size: MEALKIT_COUNT }),
  );
  const mealkits = data?.content ?? [];

  if (!isPending && mealkits.length === 0) return null;

  return (
    <SectionCard
      title="오늘의 밀키트"
      titleVariant="page"
      extra={
        <Flex vertical gap="middle">
          <Typography.Text type="secondary" className={styles.description}>
            바로 만들어 먹을 수 있는 새 밀키트예요.
          </Typography.Text>
          <Link
            to={{ pathname: ROUTES.products, search: MEALKIT_LIST_SEARCH }}
            className={styles.moreLink}
          >
            전체 보기
          </Link>
        </Flex>
      }
    >
      {isPending ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : (
        <ul className={styles.list} aria-label="오늘의 밀키트">
          {mealkits.map((product) => (
            <li key={product.productId} className={styles.item}>
              <ProductCard product={product} listSearch={MEALKIT_LIST_SEARCH} wide />
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}
