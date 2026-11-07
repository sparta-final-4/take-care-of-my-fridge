import { ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Col, Empty, Flex, Pagination, Result, Row, Skeleton } from 'antd';
import type { MouseEvent } from 'react';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';
import { SectionLoading } from '@/common/components/SectionLoading';
import { useScreens } from '@/common/hooks/useScreens';
import { ProductCard } from '@/domains/product/components/ProductCard';
import { PRICE_RANGES, PRODUCT_TYPE_LABELS } from '@/domains/product/format';
import { recordSearchClick } from '@/domains/search/api';
import { SearchFilterRow } from '@/domains/search/components/SearchFilterRow';
import {
  SEARCH_PRODUCT_PAGE_SIZE,
  SEARCH_SECTION_IDS,
  useSearchKeyword,
} from '@/domains/search/hooks/useSearchKeyword';
import { searchQueries } from '@/domains/search/queries';

const PRODUCT_TYPES = ['INGREDIENT', 'MEALKIT'] as const;

export function SearchProductSection() {
  // 한 줄 카드 수: 휴대폰 2 / md 3 / lg 4. 마우스를 쓰는 PC는 창을 줄여도 4개(useScreens).
  const screens = useScreens();
  const cardSpan = screens.lg ? 6 : screens.md ? 8 : 12;
  const {
    keyword,
    productPage,
    setProductPage,
    type,
    priceRangeKey,
    productFiltered,
    setType,
    setPriceRange,
    resetProductFilters,
    productRequest,
  } = useSearchKeyword();
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    searchQueries.products(productRequest),
  );
  // 상품을 누르면 같은 검색어·종류·가격대로 걸러진 상품 목록 위에 상품 모달이 뜬다. 모달을 닫으면 그 목록이 남는다.
  const productListSearch = `?${new URLSearchParams({
    q: keyword,
    ...(type ? { type } : {}),
    ...(priceRangeKey ? { price: priceRangeKey } : {}),
  })}`;
  const { mutate: recordClick } = useMutation({
    mutationFn: recordSearchClick,
    // 클릭 기록은 사용자가 모르게 남긴다. 실패해도 알리지 않고 상세 이동은 그대로 한다.
    meta: { skipGlobalError: true },
  });

  // 카드의 상품 링크(사진)를 누를 때만 기록한다. 카드의 글자 부분을 누른 것은 세지 않는다.
  const handleClickProduct = (productId: number) => (event: MouseEvent<HTMLElement>) => {
    const link = event.target instanceof Element ? event.target.closest('a') : null;
    if (!link || !event.currentTarget.contains(link)) return;
    recordClick({ productId, keyword });
  };

  // 페이지를 넘기면 상품 섹션 상단으로 올려 새 목록의 첫 줄부터 보이게 한다.
  const handleChangePage = (nextPage: number) => {
    setProductPage(nextPage);
    document.getElementById(SEARCH_SECTION_IDS.products)?.scrollIntoView();
  };

  const totalElements = data?.totalElements ?? 0;
  const products = data?.content ?? [];

  return (
    <SectionCard
      id={SEARCH_SECTION_IDS.products}
      title={data ? `재료·밀키트 ${totalElements.toLocaleString('ko-KR')}` : '재료·밀키트'}
      extra={
        productFiltered && (
          <Button type="link" icon={<ReloadOutlined />} onClick={resetProductFilters}>
            초기화
          </Button>
        )
      }
    >
      <Flex vertical gap="large">
        <Flex vertical gap="small">
          <SearchFilterRow
            label="종류"
            options={PRODUCT_TYPES.map((value) => ({ value, label: PRODUCT_TYPE_LABELS[value] }))}
            selected={type}
            onChange={(value) => setType(PRODUCT_TYPES.find((t) => t === value))}
          />
          <SearchFilterRow
            label="가격대"
            options={PRICE_RANGES.map((range) => ({ value: range.key, label: range.label }))}
            selected={priceRangeKey}
            onChange={(value) => setPriceRange(PRICE_RANGES.find((r) => r.key === value)?.key)}
          />
        </Flex>
        {isPending ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : isError ? (
          <Result
            status="error"
            title="상품을 불러오지 못했습니다."
            extra={<Button onClick={() => refetch()}>다시 시도</Button>}
          />
        ) : products.length === 0 ? (
          productFiltered ? (
            <Empty description={`‘${keyword}’ 중 고른 조건에 맞는 상품이 없습니다.`}>
              <Button onClick={resetProductFilters}>초기화</Button>
            </Empty>
          ) : (
            <Empty description={`‘${keyword}’에 맞는 상품이 없습니다.`}>
              <Link to={ROUTES.products}>
                <Button>전체 상품 보기</Button>
              </Link>
            </Empty>
          )
        ) : (
          <SectionLoading loading={isPlaceholderData}>
            <Flex vertical gap="large">
              <Row gutter={[16, 16]}>
                {products.map((product) => (
                  <Col
                    key={product.productId}
                    span={cardSpan}
                    onClick={
                      product.productId === undefined
                        ? undefined
                        : handleClickProduct(product.productId)
                    }
                  >
                    <ProductCard product={product} listSearch={productListSearch} />
                  </Col>
                ))}
              </Row>
              <Pagination
                align="center"
                current={productPage}
                pageSize={SEARCH_PRODUCT_PAGE_SIZE}
                total={totalElements}
                showSizeChanger={false}
                onChange={handleChangePage}
              />
            </Flex>
          </SectionLoading>
        )}
      </Flex>
    </SectionCard>
  );
}
