import { useQuery } from '@tanstack/react-query';
import { Button, Col, Empty, Flex, Pagination, Result, Row, Skeleton } from 'antd';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';
import { useAddToCart } from '@/domains/cart/hooks/useAddToCart';
import { ProductCard } from '@/domains/product/components/ProductCard';
import {
  SEARCH_PRODUCT_PAGE_SIZE,
  useSearchKeyword,
} from '@/domains/search/hooks/useSearchKeyword';
import { searchQueries } from '@/domains/search/queries';

import styles from './SearchProductSection.module.scss';

export function SearchProductSection() {
  const { keyword, productPage, setProductPage, productRequest } = useSearchKeyword();
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    searchQueries.products(productRequest),
  );
  const { addToCart, pendingProductId } = useAddToCart();

  const totalElements = data?.totalElements ?? 0;
  const products = data?.content ?? [];

  return (
    <SectionCard
      title={data ? `재료·밀키트 ${totalElements.toLocaleString('ko-KR')}` : '재료·밀키트'}
      extra={
        <Link to={`${ROUTES.products}?${new URLSearchParams({ q: keyword })}`}>
          상품 화면에서 조건 더 고르기 &gt;
        </Link>
      }
    >
      {isPending ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : isError ? (
        <Result
          status="error"
          title="상품을 불러오지 못했습니다."
          extra={<Button onClick={() => refetch()}>다시 시도</Button>}
        />
      ) : products.length === 0 ? (
        <Empty description={`‘${keyword}’에 맞는 상품이 없습니다.`}>
          <Link to={ROUTES.products}>
            <Button>전체 상품 보기</Button>
          </Link>
        </Empty>
      ) : (
        <Flex vertical gap="large" className={isPlaceholderData ? styles.loading : undefined}>
          <Row gutter={[16, 16]}>
            {products.map((product) => (
              <Col key={product.productId} xs={12} md={8} lg={6}>
                <ProductCard
                  product={product}
                  pending={pendingProductId === product.productId}
                  onAddToCart={addToCart}
                />
              </Col>
            ))}
          </Row>
          <Pagination
            align="center"
            current={productPage}
            pageSize={SEARCH_PRODUCT_PAGE_SIZE}
            total={totalElements}
            showSizeChanger={false}
            onChange={setProductPage}
          />
        </Flex>
      )}
    </SectionCard>
  );
}
