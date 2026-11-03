import { Flex, Tag, Typography } from 'antd';

import styles from './SearchFilterRow.module.scss';

interface SearchFilterRowProps {
  label: string;
  options: Array<{ value: string; label: string }>;
  selected: string | undefined;
  // 고른 칩을 다시 누르면 undefined로 해제한다.
  onChange: (value: string | undefined) => void;
}

// 결과 섹션 위의 칩 한 줄. 칩은 하나만 고를 수 있다.
export function SearchFilterRow({ label, options, selected, onChange }: SearchFilterRowProps) {
  return (
    <div className={styles.row}>
      <Typography.Text type="secondary" className={styles.label}>
        {label}
      </Typography.Text>
      <Flex gap="small" wrap>
        {options.map((option) => (
          <Tag.CheckableTag
            key={option.value}
            checked={selected === option.value}
            onChange={(checked) => onChange(checked ? option.value : undefined)}
            className={styles.chip}
          >
            {option.label}
          </Tag.CheckableTag>
        ))}
      </Flex>
    </div>
  );
}
