import { CloseOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Input, Select, Tag, Typography } from 'antd';

import { COOK_TIME_OPTIONS } from '@/domains/recipe/format';
import { useRecipeFilters } from '@/domains/recipe/hooks/useRecipeFilters';
import { recipeQueries } from '@/domains/recipe/queries';

import styles from './RecipeSearchSection.module.scss';

interface FilterRow {
  label: string;
  options: Array<{ value: string; label: string }>;
  // 여러 개 고르는 묶음(음식 종류·요리 분류)은 values, 하나만 고르는 묶음(조리 시간)은 value를 쓴다.
  values?: string[];
  onChangeValues?: (values: string[]) => void;
  value?: string;
  onChangeValue?: (value: string | undefined) => void;
  // 항목이 많은 묶음은 글자를 쳐서 찾을 수 있게 한다.
  searchable?: boolean;
}

export function RecipeSearchSection() {
  const {
    keyword,
    cuisineTypes,
    categories,
    cookTimeKey,
    setKeyword,
    setCuisineTypes,
    setCategories,
    setCookTime,
    filtered,
    reset,
  } = useRecipeFilters();
  // 분류를 못 받아 오면 해당 드롭다운만 감추고 검색, 조리 시간 필터는 그대로 쓴다.
  const { data: classifications } = useQuery(recipeQueries.classifications());

  const toOptions = (values: string[] | undefined) =>
    (values ?? []).map((value) => ({ value, label: value }));

  // 음식 종류·요리 분류는 드롭다운에서 여러 개 체크하고, 고른 값은 드롭다운 아래 칩(×로 해제)으로 보인다.
  // 조리 시간은 '최대 n분'이라 하나만 고른다. 항목이 많은 요리 분류는 맨 아래에 둔다.
  const filterRows: FilterRow[] = [
    {
      label: '음식 종류',
      options: toOptions(classifications?.cuisineTypes),
      values: cuisineTypes,
      onChangeValues: setCuisineTypes,
    },
    {
      label: '조리 시간',
      options: COOK_TIME_OPTIONS.map((option) => ({ value: option.key, label: option.label })),
      value: cookTimeKey,
      onChangeValue: (value) =>
        setCookTime(COOK_TIME_OPTIONS.find((option) => option.key === value)?.key),
    },
    {
      label: '요리 분류',
      options: toOptions(classifications?.categories),
      values: categories,
      onChangeValues: setCategories,
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
            {row.onChangeValues ? (
              <>
                <Select
                  mode="multiple"
                  aria-label={row.label}
                  placeholder="전체"
                  allowClear
                  showSearch={row.searchable ? { optionFilterProp: 'label' } : false}
                  options={row.options}
                  value={row.values}
                  onChange={row.onChangeValues}
                  // 고른 값은 아래 칩으로 보여 주므로 상자 안에는 개수만 둔다.
                  maxTagCount={0}
                  maxTagPlaceholder={(omitted) => `${omitted.length}개 선택`}
                  className={styles.select}
                />
                {row.values && row.values.length > 0 && (
                  <ul className={styles.chips} aria-label={`고른 ${row.label}`}>
                    {row.values.map((value) => (
                      <li key={value}>
                        <Tag
                          closable
                          closeIcon={<CloseOutlined aria-label={`${value} 해제`} />}
                          onClose={(event) => {
                            // Tag가 스스로 숨지 않게 막고, URL에서 값을 빼 다시 그린다.
                            event.preventDefault();
                            row.onChangeValues?.(
                              row.values?.filter((item) => item !== value) ?? [],
                            );
                          }}
                          className={styles.chip}
                        >
                          {value}
                        </Tag>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            ) : (
              <Select
                aria-label={row.label}
                placeholder="전체"
                allowClear
                options={row.options}
                value={row.value ?? null}
                onChange={(value: string | null | undefined) =>
                  row.onChangeValue?.(value ?? undefined)
                }
                className={styles.select}
              />
            )}
          </div>
        ))}
    </section>
  );
}
