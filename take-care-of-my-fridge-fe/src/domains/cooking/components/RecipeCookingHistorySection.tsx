import { Button, Flex, Spin, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState } from 'react';

import type { CookingLogResponse } from '@/common/api/clients';
import { cookingQueries } from '../queries';
import { CookingLogEditModal } from './CookingLogEditModal';

import styles from './MyRecipeListSection.module.scss';

export interface RecipeCookingHistorySectionProps {
  recipeId: number;
}

export function RecipeCookingHistorySection({ recipeId }: RecipeCookingHistorySectionProps) {
  const [editingLog, setEditingLog] = useState<CookingLogResponse | null>(null);

  // 펼쳐졌을 때만 렌더링되므로 마운트 시 조회된다.
  const { data: logsPage, isLoading } = useQuery(cookingQueries.logs({ recipeId }));
  const logs = logsPage?.content ?? [];

  return (
    <div className={styles.detailHistorySection}>
      <Typography.Text type="secondary" className={styles.detailLabel}>
        요리 기록
      </Typography.Text>

      {isLoading ? (
        <Flex align="center" gap="small" className={styles.historyLoadingWrapper}>
          <Spin size="small" />
          <Typography.Text type="secondary" className={styles.historyEmptyText}>
            요리 기록을 불러오는 중...
          </Typography.Text>
        </Flex>
      ) : logs.length === 0 ? (
        <Typography.Text type="secondary" className={styles.historyEmptyText}>
          요리 기록이 없습니다.
        </Typography.Text>
      ) : (
        <div className={styles.historyList}>
          {logs.map((log) => {
            const isSuccess = log.result === 'SUCCESS';
            return (
              <div key={log.id} className={styles.historyItem}>
                <span className={styles.historyDate}>
                  {log.createdAt ? dayjs(log.createdAt).format('MM.DD') : '-'}
                </span>
                <Tag
                  color={isSuccess ? 'default' : undefined}
                  className={isSuccess ? styles.successTag : styles.failedTag}
                >
                  {isSuccess ? '성공' : '실패'}
                </Tag>
                <span className={styles.historyMemo}>{log.memo || '-'}</span>
                <Button
                  type="link"
                  size="small"
                  className={styles.historyEditButton}
                  onClick={() => setEditingLog(log)}
                >
                  수정
                </Button>
              </div>
            );
          })}
        </div>
      )}

      <CookingLogEditModal
        open={Boolean(editingLog)}
        log={editingLog}
        onClose={() => setEditingLog(null)}
      />
    </div>
  );
}
