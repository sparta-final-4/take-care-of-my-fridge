import { Button, Input, Tag, Typography } from 'antd';

import { PRICE_RANGES, PRODUCT_TYPE_LABELS } from '@/domains/product/format';
import { useProductFilters } from '@/domains/product/hooks/useProductFilters';

import styles from './ProductSearchSection.module.scss';

const PRODUCT_TYPES = ['INGREDIENT', 'MEALKIT'] as const;

// 상품 목록의 필터 칸(넓은 화면은 왼쪽 칸, 좁은 화면은 필터 서랍 안).
// 레퍼런스처럼 검색 · 종류 · 가격대를 얇은 선으로 나눈 묶음으로 두고, 종류·가격대는 앞에 점이 붙은 글자 목록으로 고른다.
export function ProductSearchSection() {
  const { keyword, type, priceRangeKey, filtered, setKeyword, setType, setPriceRange, reset } =
    useProductFilters();

  return (
    <section className={styles.facets} aria-label="상품 필터">
      <div className={styles.group}>
        {/* 첫 묶음 이름 줄 오른쪽에 초기화를 둔다(칸 제목은 따로 두지 않는다). */}
        <div className={styles.groupHead}>
          <Typography.Text className={styles.groupLabel}>검색</Typography.Text>
          {filtered && (
            <Button type="link" onClick={reset} className={styles.reset}>
              초기화
            </Button>
          )}
        </div>
        {/* 입력값은 검색을 누를 때만 URL에 반영한다. 뒤로 가기로 검색어가 바뀌면 key로 입력칸을 다시 채운다. */}
        <Input.Search
          key={keyword}
          defaultValue={keyword}
          placeholder="재료나 밀키트 이름"
          aria-label="상품 검색"
          allowClear
          maxLength={50}
          onSearch={setKeyword}
        />
      </div>

      <div className={styles.group}>
        <Typography.Text className={styles.groupLabel}>종류</Typography.Text>
        <div className={styles.options}>
          {PRODUCT_TYPES.map((value) => (
            <Tag.CheckableTag
              key={value}
              checked={type === value}
              onChange={(checked) => setType(checked ? value : undefined)}
              className={styles.option}
            >
              {PRODUCT_TYPE_LABELS[value]}
            </Tag.CheckableTag>
          ))}
        </div>
      </div>

      <div className={styles.group}>
        <Typography.Text className={styles.groupLabel}>가격대</Typography.Text>
        <div className={styles.options}>
          {PRICE_RANGES.map((range) => (
            <Tag.CheckableTag
              key={range.key}
              checked={priceRangeKey === range.key}
              onChange={(checked) => setPriceRange(checked ? range.key : undefined)}
              className={styles.option}
            >
              {range.label}
            </Tag.CheckableTag>
          ))}
        </div>
      </div>
    </section>
  );
}
