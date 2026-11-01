import { EditOutlined } from '@ant-design/icons';
import { Button, Flex, Spin, Tag, Tooltip, Typography } from 'antd';
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
  // 닫히는 동안에도 팝업이 기록 내용을 유지하도록 열림 상태와 기록을 따로 둔다.
  const [editingLog, setEditingLog] = useState<CookingLogResponse | null>(null);
  const [editOpen, setEditOpen] = useState(false);

  const handleEdit = (log: CookingLogResponse) => {
    setEditingLog(log);
    setEditOpen(true);
  };

  // 펼쳐졌을 때만 렌더링되므로 마운트 시 조회된다.
  const {
    data: logsPage,
    isLoading,
    isError,
    refetch,
  } = useQuery(cookingQueries.logs({ recipeId }));
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
      ) : isError ? (
        <Flex align="center" gap="small" className={styles.historyLoadingWrapper}>
          <Typography.Text type="secondary" className={styles.historyEmptyText}>
            요리 기록을 불러오지 못했어요.
          </Typography.Text>
          <Button size="small" onClick={() => void refetch()}>
            다시 시도
          </Button>
        </Flex>
      ) : logs.length === 0 ? (
        <Typography.Text type="secondary" className={styles.historyEmptyText}>
          요리 기록이 없습니다.
        </Typography.Text>
      ) : (
        <div className={styles.historyList}>
          {logs.map((log) => {
            const isSuccess = log.result === 'SUCCESS';
            const dateText = log.createdAt ? dayjs(log.createdAt).format('MM.DD') : '-';
            return (
              <div key={log.id} className={styles.historyItem}>
                {/* 날짜·메모는 내 후기 줄과 같은 Typography.Text + 14px(클래스에서 직접 지정) */}
                <Typography.Text type="secondary" className={styles.historyDate}>
                  {dateText}
                </Typography.Text>
                {/* success·error 상태색은 연한 배경에 밝은 글자라 잘 안 보여, 글자가 진한 기본 색상(green·red)을 쓴다. */}
                <Tag
                  variant="filled"
                  color={isSuccess ? 'green' : 'red'}
                  className={styles.historyResultTag}
                >
                  {isSuccess ? '성공' : '실패'}
                </Tag>
                {/* 메모 바로 옆에 수정 버튼(위쪽 후기 수정 연필과 같은 모양) */}
                <Flex align="center" gap="small" className={styles.historyMemoLine}>
                  {log.memo ? (
                    <Typography.Text className={styles.historyMemo}>{log.memo}</Typography.Text>
                  ) : (
                    <Typography.Text type="secondary" className={styles.historyMemoEmpty}>
                      메모 없음
                    </Typography.Text>
                  )}
                  <Tooltip title="요리 기록 수정">
                    <Button
                      type="text"
                      size="small"
                      icon={<EditOutlined />}
                      aria-label={`${dateText} 요리 기록 수정`}
                      onClick={() => handleEdit(log)}
                    />
                  </Tooltip>
                </Flex>
              </div>
            );
          })}
        </div>
      )}

      <CookingLogEditModal open={editOpen} log={editingLog} onClose={() => setEditOpen(false)} />
    </div>
  );
}
