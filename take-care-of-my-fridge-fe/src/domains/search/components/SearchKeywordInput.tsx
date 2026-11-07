import { CloseOutlined, SearchOutlined } from '@ant-design/icons';
import { AutoComplete, type AutoCompleteProps, Button, Flex, Input, Typography } from 'antd';
import { type KeyboardEvent, type MouseEvent, useRef, useState } from 'react';

import { useRecentKeywords } from '../hooks/useRecentKeywords';
import { useSearchSuggestions } from '../hooks/useSearchSuggestions';
import { addRecentKeyword, clearRecentKeywords, removeRecentKeyword } from '../recentKeywords';

import styles from './SearchKeywordInput.module.scss';

// 백엔드 검색어 길이 제한(RecipeListRequest.keyword)
const SEARCH_KEYWORD_MAX_LENGTH = 50;

interface SearchKeywordInputProps {
  initialKeyword: string;
  ariaLabel: string;
  className?: string;
  onSearch: (keyword: string) => void;
}

// 상단 통합 검색창. 비어 있으면 최근 검색어를, 입력하면 레시피 이름 후보를 보여 주고,
// 목록에서 고르거나 Enter를 누르면 검색한다.
export function SearchKeywordInput({
  initialKeyword,
  ariaLabel,
  className,
  onSearch,
}: SearchKeywordInputProps) {
  const [value, setValue] = useState(initialKeyword);
  const [open, setOpen] = useState(false);
  const suggestions = useSearchSuggestions(value);
  const recentKeywords = useRecentKeywords();
  // 후보에서 Enter를 누르면 후보 선택과 입력칸 Enter가 함께 일어나 후보만 검색한다.
  const selectedRef = useRef(false);

  // 기본 동작은 글자가 있을 때만 목록을 여니, 다 지웠을 때도 최근 검색어가 보이게 직접 연다.
  const handleChange = (next: string) => {
    setValue(next);
    if (!next.trim()) setOpen(true);
  };
  const search = (keyword: string) => {
    addRecentKeyword(keyword);
    onSearch(keyword);
  };
  const handleSelect = (keyword: string) => {
    selectedRef.current = true;
    setValue(keyword);
    search(keyword);
  };
  const handlePressEnter = (event: KeyboardEvent<HTMLInputElement>) => {
    const typed = event.currentTarget.value;
    setTimeout(() => {
      if (!selectedRef.current) search(typed);
      selectedRef.current = false;
    });
  };
  // 삭제 버튼은 목록 안에 있어 누르면 항목 선택(검색)과 입력칸 포커스 해제가 함께 일어나므로 막는다.
  const keepFocus = (event: MouseEvent) => event.preventDefault();
  const handleRemove = (event: MouseEvent, keyword: string) => {
    event.stopPropagation();
    removeRecentKeyword(keyword);
  };

  const options: AutoCompleteProps['options'] =
    value.trim() || recentKeywords.length === 0
      ? suggestions.map((name) => ({ value: name }))
      : [
          {
            label: (
              <Flex justify="space-between" align="center">
                <span>최근 검색어</span>
                <Button
                  type="link"
                  size="small"
                  onMouseDown={keepFocus}
                  onClick={clearRecentKeywords}
                >
                  전체 삭제
                </Button>
              </Flex>
            ),
            options: recentKeywords.map((keyword) => ({
              value: keyword,
              label: (
                <Flex justify="space-between" align="center" gap="small">
                  <Typography.Text ellipsis>{keyword}</Typography.Text>
                  <Button
                    type="text"
                    size="small"
                    icon={<CloseOutlined />}
                    aria-label={`최근 검색어 ${keyword} 삭제`}
                    onMouseDown={keepFocus}
                    onClick={(event) => handleRemove(event, keyword)}
                  />
                </Flex>
              ),
            })),
          },
        ];

  return (
    <AutoComplete
      value={value}
      options={options}
      open={open}
      onOpenChange={setOpen}
      onChange={handleChange}
      onSelect={handleSelect}
      defaultActiveFirstOption={false}
      className={styles.autoComplete}
    >
      <Input
        prefix={<SearchOutlined />}
        placeholder="SEARCH FOR..."
        aria-label={ariaLabel}
        maxLength={SEARCH_KEYWORD_MAX_LENGTH}
        enterKeyHint="search"
        onPressEnter={handlePressEnter}
        className={className}
      />
    </AutoComplete>
  );
}
