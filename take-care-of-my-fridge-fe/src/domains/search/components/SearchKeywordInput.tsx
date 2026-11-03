import { SearchOutlined } from '@ant-design/icons';
import { AutoComplete, Input } from 'antd';
import { type KeyboardEvent, useRef, useState } from 'react';

import { useSearchSuggestions } from '../hooks/useSearchSuggestions';

import styles from './SearchKeywordInput.module.scss';

// 백엔드 검색어 길이 제한(RecipeListRequest.keyword)
const SEARCH_KEYWORD_MAX_LENGTH = 50;

interface SearchKeywordInputProps {
  initialKeyword: string;
  ariaLabel: string;
  className?: string;
  onSearch: (keyword: string) => void;
}

// 상단 통합 검색창. 입력하면 레시피 이름 후보를 보여 주고, 후보를 고르거나 Enter를 누르면 검색한다.
export function SearchKeywordInput({
  initialKeyword,
  ariaLabel,
  className,
  onSearch,
}: SearchKeywordInputProps) {
  const [value, setValue] = useState(initialKeyword);
  const suggestions = useSearchSuggestions(value);
  // 후보에서 Enter를 누르면 후보 선택과 입력칸 Enter가 함께 일어나 후보만 검색한다.
  const selectedRef = useRef(false);

  const handleSelect = (keyword: string) => {
    selectedRef.current = true;
    setValue(keyword);
    onSearch(keyword);
  };
  const handlePressEnter = (event: KeyboardEvent<HTMLInputElement>) => {
    const typed = event.currentTarget.value;
    setTimeout(() => {
      if (!selectedRef.current) onSearch(typed);
      selectedRef.current = false;
    });
  };

  return (
    <AutoComplete
      value={value}
      options={suggestions.map((name) => ({ value: name }))}
      onChange={setValue}
      onSelect={handleSelect}
      defaultActiveFirstOption={false}
      className={styles.autoComplete}
    >
      <Input
        prefix={<SearchOutlined />}
        placeholder="재료나 요리를 검색해 보세요"
        aria-label={ariaLabel}
        maxLength={SEARCH_KEYWORD_MAX_LENGTH}
        enterKeyHint="search"
        onPressEnter={handlePressEnter}
        className={className}
      />
    </AutoComplete>
  );
}
