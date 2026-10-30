import { Flex, Input, Tag, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { PRICE_RANGES, PRODUCT_TYPE_LABELS } from '@/domains/product/format';
import { useProductFilters } from '@/domains/product/hooks/useProductFilters';

import styles from './ProductSearchSection.module.scss';

const PRODUCT_TYPES = ['INGREDIENT', 'MEALKIT'] as const;

export function ProductSearchSection() {
  const { keyword, type, priceRangeKey, setKeyword, setType, setPriceRange } = useProductFilters();

  return (
    <SectionCard title="어떤 재료가 필요하세요?" bracket={false}>
      <Flex vertical gap="middle">
        {/* 입력값은 검색을 누를 때만 URL에 반영한다. 뒤로 가기로 검색어가 바뀌면 key로 입력칸을 다시 채운다. */}
        <Input.Search
          key={keyword}
          defaultValue={keyword}
          size="large"
          placeholder="재료나 밀키트 이름 (예: 대파, 두부, 파스타 밀키트)"
          aria-label="상품 검색"
          enterButton="검색"
          allowClear
          maxLength={50}
          onSearch={setKeyword}
        />
        <Flex align="center" gap="small" wrap>
          <Typography.Text type="secondary" className={styles.label}>
            종류
          </Typography.Text>
          {PRODUCT_TYPES.map((value) => (
            <Tag.CheckableTag
              key={value}
              checked={type === value}
              onChange={(checked) => setType(checked ? value : undefined)}
              className={styles.chip}
            >
              {PRODUCT_TYPE_LABELS[value]}
            </Tag.CheckableTag>
          ))}
        </Flex>
        <Flex align="center" gap="small" wrap>
          <Typography.Text type="secondary" className={styles.label}>
            가격대
          </Typography.Text>
          {PRICE_RANGES.map((range) => (
            <Tag.CheckableTag
              key={range.key}
              checked={priceRangeKey === range.key}
              onChange={(checked) => setPriceRange(checked ? range.key : undefined)}
              className={styles.chip}
            >
              {range.label}
            </Tag.CheckableTag>
          ))}
        </Flex>
      </Flex>
    </SectionCard>
  );
}
