import { useQuery } from '@tanstack/react-query';
import { Button, Col, Empty, Flex, Pagination, Result, Row, Segmented, Skeleton } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { SectionLoading } from '@/common/components/SectionLoading';
import { useAddToCart } from '@/domains/cart/hooks/useAddToCart';
import { PRODUCT_SORT_OPTIONS, useProductFilters } from '@/domains/product/hooks/useProductFilters';
import { productQueries } from '@/domains/product/queries';

import { ProductCard } from './ProductCard';

const PAGE_SIZE = 16;

export function ProductListSection() {
  const { keyword, request, filtered, sort, page, setSort, setPage, reset } = useProductFilters();
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    productQueries.list({ ...request, size: PAGE_SIZE }),
  );
  const { addToCart, pendingProductId } = useAddToCart();

  const handleChangePage = (nextPage: number) => {
    setPage(nextPage);
    window.scrollTo({ top: 0 });
  };

  const totalElements = data?.totalElements ?? 0;
  const products = data?.content ?? [];
  const label = keyword ? `'${keyword}' 검색 결과` : filtered ? '조건에 맞는 상품' : '전체 상품';

  return (
    <SectionCard
      title={data ? `${label} ${totalElements.toLocaleString('ko-KR')}개` : label}
      bracket={false}
      extra={
        <Segmented
          size="small"
          options={PRODUCT_SORT_OPTIONS}
          value={sort}
          onChange={setSort}
          aria-label="정렬"
        />
      }
    >
      {isPending ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : isError ? (
        <Result
          status="error"
          title="상품을 불러오지 못했습니다."
          extra={<Button onClick={() => refetch()}>다시 시도</Button>}
        />
      ) : products.length === 0 ? (
        <Empty
          description={filtered ? '조건에 맞는 상품이 없습니다.' : '판매 중인 상품이 없습니다.'}
        >
          {filtered && <Button onClick={reset}>초기화</Button>}
        </Empty>
      ) : (
        <SectionLoading loading={isPlaceholderData}>
          <Flex vertical gap="large">
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
              current={page}
              pageSize={PAGE_SIZE}
              total={totalElements}
              showSizeChanger={false}
              onChange={handleChangePage}
            />
          </Flex>
        </SectionLoading>
      )}
    </SectionCard>
  );
}
