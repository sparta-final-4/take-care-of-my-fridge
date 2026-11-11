import { useQuery } from '@tanstack/react-query';
import { Button, Input, Select, Typography } from 'antd';

import { COOK_TIME_OPTIONS } from '@/domains/recipe/format';
import { useRecipeFilters } from '@/domains/recipe/hooks/useRecipeFilters';
import { recipeQueries } from '@/domains/recipe/queries';

import styles from './RecipeSearchSection.module.scss';

interface FilterRow {
  label: string;
  value: string | undefined;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string | undefined) => void;
  // 항목이 많은 묶음은 글자를 쳐서 찾을 수 있게 한다.
  searchable?: boolean;
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
  // 분류를 못 받아 오면 해당 드롭다운만 감추고 검색, 조리 시간 필터는 그대로 쓴다.
  const { data: classifications } = useQuery(recipeQueries.classifications());

  const toOptions = (values: string[] | undefined) =>
    (values ?? []).map((value) => ({ value, label: value }));

  // 묶음마다 드롭다운 하나에서 값을 하나 고르고, 지우기(×)로 해제한다.
  // 항목이 많은 요리 분류는 맨 아래에 둬 짧은 조건이 먼저 보이게 한다.
  const filterRows: FilterRow[] = [
    {
      label: '음식 종류',
      value: cuisineType,
      options: toOptions(classifications?.cuisineTypes),
      onChange: setCuisineType,
    },
    {
      label: '조리 시간',
      value: cookTimeKey,
      options: COOK_TIME_OPTIONS.map((option) => ({ value: option.key, label: option.label })),
      onChange: (value) =>
        setCookTime(COOK_TIME_OPTIONS.find((option) => option.key === value)?.key),
    },
    {
      label: '요리 분류',
      value: category,
      options: toOptions(classifications?.categories),
      onChange: setCategory,
      searchable: true,
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
        .filter((row) => row.options.length > 0)
        .map((row) => (
          <div key={row.label} className={styles.group}>
            <Typography.Text className={styles.groupLabel}>{row.label}</Typography.Text>
            <Select
              aria-label={row.label}
              placeholder="전체"
              allowClear
              showSearch={row.searchable ? { optionFilterProp: 'label' } : false}
              options={row.options}
              value={row.value ?? null}
              onChange={(value: string | null | undefined) => row.onChange(value ?? undefined)}
              className={styles.select}
            />
          </div>
        ))}
    </section>
  );
}
