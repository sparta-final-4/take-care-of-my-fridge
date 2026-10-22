import { Flex, Input, Tag, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { CATEGORY_OPTIONS, COOK_TIME_OPTIONS, CUISINE_OPTIONS } from '@/domains/recipe/format';
import { useRecipeFilters } from '@/domains/recipe/hooks/useRecipeFilters';

import styles from './RecipeSearchSection.module.scss';

export function RecipeSearchSection() {
  const {
    keyword,
    cuisineType,
    category,
    cookTimeKey,
    setKeyword,
    setCuisineType,
    setCategory,
    setCookTime,
  } = useRecipeFilters();

  // 칩은 하나만 고를 수 있고, 고른 칩을 다시 누르면 해제한다.
  const filterRows = [
    {
      label: '음식 종류',
      chips: CUISINE_OPTIONS.map((value) => ({
        key: value,
        label: value,
        checked: cuisineType === value,
        onChange: (checked: boolean) => setCuisineType(checked ? value : undefined),
      })),
    },
    {
      label: '요리 분류',
      chips: CATEGORY_OPTIONS.map((value) => ({
        key: value,
        label: value,
        checked: category === value,
        onChange: (checked: boolean) => setCategory(checked ? value : undefined),
      })),
    },
    {
      label: '조리 시간',
      chips: COOK_TIME_OPTIONS.map((option) => ({
        key: option.key,
        label: option.label,
        checked: cookTimeKey === option.key,
        onChange: (checked: boolean) => setCookTime(checked ? option.key : undefined),
      })),
    },
  ];

  return (
    <SectionCard
      title="무엇을 찾고 있나요?"
      extra={
        <Typography.Text type="secondary" className={styles.hint}>
          조건을 고르면 아래 목록이 바로 바뀌어요
        </Typography.Text>
      }
    >
      <Flex vertical gap="middle">
        {/* 입력값은 검색을 누를 때만 URL에 반영한다. 뒤로 가기로 검색어가 바뀌면 key로 입력칸을 다시 채운다. */}
        <Input.Search
          key={keyword}
          defaultValue={keyword}
          size="large"
          placeholder="레시피 이름 (예: 된장찌개, 계란 볶음밥)"
          aria-label="레시피 검색"
          enterButton="검색"
          allowClear
          maxLength={50}
          onSearch={setKeyword}
        />
        {filterRows.map((row) => (
          <div key={row.label} className={styles.filterRow}>
            <Typography.Text type="secondary" className={styles.label}>
              {row.label}
            </Typography.Text>
            <Flex gap="small" wrap>
              {row.chips.map((chip) => (
                <Tag.CheckableTag
                  key={chip.key}
                  checked={chip.checked}
                  onChange={chip.onChange}
                  className={styles.chip}
                >
                  {chip.label}
                </Tag.CheckableTag>
              ))}
            </Flex>
          </div>
        ))}
      </Flex>
    </SectionCard>
  );
}
