import { useQuery } from '@tanstack/react-query';
import { Button, Input, Tag, Typography } from 'antd';
import { useState } from 'react';

import { COOK_TIME_OPTIONS } from '@/domains/recipe/format';
import { useRecipeFilters } from '@/domains/recipe/hooks/useRecipeFilters';
import { recipeQueries } from '@/domains/recipe/queries';

import styles from './RecipeSearchSection.module.scss';

const CATEGORY_PREVIEW_COUNT = 10;

interface FilterRow {
  label: string;
  chips: Array<{
    key: string;
    label: string;
    checked: boolean;
    onChange: (checked: boolean) => void;
  }>;
  toggle?: { expanded: boolean; onToggle: () => void };
  // 칩을 두 칸으로 나눠 세로로 너무 길어지지 않게 한다.
  twoColumns?: boolean;
}

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
    filtered,
    reset,
  } = useRecipeFilters();
  // 분류를 못 받아 오면 해당 칩 줄만 비워 두고 검색, 조리 시간 필터는 그대로 쓴다.
  const { data: classifications } = useQuery(recipeQueries.classifications());
  const [showAllCategories, setShowAllCategories] = useState(false);

  // 요리 분류는 수가 많아 앞부분만 보이고, 접혀 있어도 고른 분류는 함께 보인다.
  const categories = classifications?.categories ?? [];
  const visibleCategories = showAllCategories
    ? categories
    : categories.filter((value, index) => index < CATEGORY_PREVIEW_COUNT || value === category);

  // 칩은 하나만 고를 수 있고, 고른 칩을 다시 누르면 해제한다.
  // 항목이 많은 요리 분류는 맨 아래에 둬 짧은 조건이 먼저 보이게 한다.
  const filterRows: FilterRow[] = [
    {
      label: '음식 종류',
      chips: (classifications?.cuisineTypes ?? []).map((value) => ({
        key: value,
        label: value,
        checked: cuisineType === value,
        onChange: (checked: boolean) => setCuisineType(checked ? value : undefined),
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
    {
      label: '요리 분류',
      chips: visibleCategories.map((value) => ({
        key: value,
        label: value,
        checked: category === value,
        onChange: (checked: boolean) => setCategory(checked ? value : undefined),
      })),
      toggle:
        categories.length > CATEGORY_PREVIEW_COUNT
          ? {
              expanded: showAllCategories,
              onToggle: () => setShowAllCategories((prev) => !prev),
            }
          : undefined,
      twoColumns: true,
    },
  ];

  return (
    <section className={styles.facets} aria-label="레시피 검색 조건">
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
          placeholder="레시피 이름 (예: 된장찌개)"
          aria-label="레시피 검색"
          allowClear
          maxLength={50}
          onSearch={setKeyword}
        />
      </div>
      {/* 분류를 못 받아 와 고를 것이 없는 묶음은 감춘다. */}
      {filterRows
        .filter((row) => row.chips.length > 0)
        .map((row) => (
          <div key={row.label} className={styles.group}>
            <Typography.Text className={styles.groupLabel}>{row.label}</Typography.Text>
            <div
              className={row.twoColumns ? `${styles.options} ${styles.twoColumns}` : styles.options}
            >
              {row.chips.map((chip) => (
                <Tag.CheckableTag
                  key={chip.key}
                  checked={chip.checked}
                  onChange={chip.onChange}
                  className={styles.option}
                >
                  {chip.label}
                </Tag.CheckableTag>
              ))}
              {row.toggle && (
                <Button
                  type="link"
                  className={styles.toggle}
                  aria-expanded={row.toggle.expanded}
                  onClick={row.toggle.onToggle}
                >
                  {row.toggle.expanded ? '접기' : '더보기'}
                </Button>
              )}
            </div>
          </div>
        ))}
    </section>
  );
}
