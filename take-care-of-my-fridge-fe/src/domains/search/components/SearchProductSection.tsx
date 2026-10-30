import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Col, Empty, Flex, Pagination, Result, Row, Skeleton } from 'antd';
import type { MouseEvent } from 'react';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';
import { SectionLoading } from '@/common/components/SectionLoading';
import { useAddToCart } from '@/domains/cart/hooks/useAddToCart';
import { ProductCard } from '@/domains/product/components/ProductCard';
import { recordSearchClick } from '@/domains/search/api';
import {
  SEARCH_PRODUCT_PAGE_SIZE,
  SEARCH_SECTION_IDS,
  useSearchKeyword,
} from '@/domains/search/hooks/useSearchKeyword';
import { searchQueries } from '@/domains/search/queries';

export function SearchProductSection() {
  const { keyword, productPage, setProductPage, productRequest } = useSearchKeyword();
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    searchQueries.products(productRequest),
  );
  const { addToCart, pendingProductId } = useAddToCart();
  const { mutate: recordClick } = useMutation({
    mutationFn: recordSearchClick,
    // 클릭 기록은 사용자가 모르게 남긴다. 실패해도 알리지 않고 상세 이동은 그대로 한다.
    meta: { skipGlobalError: true },
  });

  // 카드의 상세 링크를 누를 때만 기록한다. 장바구니 담기 버튼은 링크 밖이라 세지 않는다.
  const handleClickProduct = (productId: number) => (event: MouseEvent<HTMLElement>) => {
    const link = event.target instanceof Element ? event.target.closest('a') : null;
    if (!link || !event.currentTarget.contains(link)) return;
    recordClick({ productId, keyword });
  };

  const totalElements = data?.totalElements ?? 0;
  const products = data?.content ?? [];

  return (
    <SectionCard
      id={SEARCH_SECTION_IDS.products}
      title={data ? `재료·밀키트 ${totalElements.toLocaleString('ko-KR')}` : '재료·밀키트'}
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
        <SectionLoading loading={isPlaceholderData}>
          <Flex vertical gap="large">
            <Row gutter={[16, 16]}>
              {products.map((product) => (
                <Col
                  key={product.productId}
                  xs={12}
                  md={8}
                  lg={6}
                  onClick={
                    product.productId === undefined
                      ? undefined
                      : handleClickProduct(product.productId)
                  }
                >
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
        </SectionLoading>
      )}
    </SectionCard>
  );
}
