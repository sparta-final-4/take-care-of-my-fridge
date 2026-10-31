import { CheckOutlined } from '@ant-design/icons';
import { App, Button, Empty, Flex, Result, Segmented, Select, Skeleton, Typography } from 'antd';
import { useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';
import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';

import type { FridgeItemResponse, FridgeSortType } from '../api';
import { FRIDGE_TABS, isFridgeTab, matchesFridgeTab, type FridgeTab } from '../filter';
import { STORAGE_LABELS } from '../format';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { readNewFridgeIds } from '../newItems';
import { useFridgeItems, useRemoveFridgeItem, useTakeOutFridgeItem } from '../queries';
import { FridgeItemConsumeModal } from './FridgeItemConsumeModal';
import { FridgeItemEditModal } from './FridgeItemEditModal';
import { FridgeItemRow } from './FridgeItemRow';
import styles from './FridgeItemListSection.module.scss';

const TAB_LABELS: Record<FridgeTab, string> = {
  ALL: '전체',
  ...STORAGE_LABELS,
  MEALKIT: '밀키트',
  BASIC_SEASONING: '기본 양념',
};

// 전체 탭은 목록에 기본 양념만 있을 때 비어 보인다.
const EMPTY_TAB_MESSAGES: Record<FridgeTab, string> = {
  ALL: '기본 양념 말고는 재료가 없어요',
  REFRIGERATED: `${STORAGE_LABELS.REFRIGERATED} 보관 재료가 없어요`,
  FROZEN: `${STORAGE_LABELS.FROZEN} 보관 재료가 없어요`,
  ROOM_TEMP: `${STORAGE_LABELS.ROOM_TEMP} 보관 재료가 없어요`,
  MEALKIT: '밀키트가 없어요',
  BASIC_SEASONING: '기본 양념이 없어요',
};

const SORT_OPTIONS: Array<{ value: FridgeSortType; label: string }> = [
  { value: 'EXPIRATION', label: '임박순' },
  { value: 'CREATED', label: '최신 등록순' },
  { value: 'NAME', label: '이름순' },
];

// 임박순 그룹. 서버가 준 status·dDay만으로 나눈다.
const EXPIRATION_GROUPS: Array<{ label: string; match: (item: FridgeItemResponse) => boolean }> = [
  { label: '기한 지남', match: (item) => item.status === 'EXPIRED' },
  { label: '오늘까지', match: (item) => item.status === 'IMMINENT' && item.dDay === 0 },
  { label: '3일 이내', match: (item) => item.status === 'IMMINENT' && (item.dDay ?? 0) > 0 },
  { label: '여유 있음', match: (item) => item.status === 'NORMAL' },
];

export function FridgeItemListSection() {
  const openChat = useChatModalStore((state) => state.openChat);
  const { message } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [sort, setSort] = useFridgeSort();
  const { data, isPending, isError, refetch } = useFridgeItems(sort, true);
  const takeOutMutation = useTakeOutFridgeItem();
  const removeMutation = useRemoveFridgeItem();
  const [checkedIds, setCheckedIds] = useState<number[]>([]);
  const [removingIds, setRemovingIds] = useState<number[]>([]);
  const [editingItem, setEditingItem] = useState<FridgeItemResponse>();
  const [consumingItem, setConsumingItem] = useState<FridgeItemResponse>();

  // 기존 링크가 그대로 열리도록 키는 storage로 두고 밀키트·기본 양념 탭 값만 더했다.
  const storageParam = searchParams.get('storage');
  const storage: FridgeTab = isFridgeTab(storageParam) ? storageParam : 'ALL';
  // 메인에서 등록하고 넘어왔을 때만 있다. 정렬·탭을 바꾸면 state 없이 이동해 사라진다.
  const newFridgeIds = readNewFridgeIds(location.state);

  const handleStorageChange = (next: FridgeTab) => {
    setSearchParams(
      (params) => {
        if (next === 'ALL') params.delete('storage');
        else params.set('storage', next);
        return params;
      },
      { replace: true },
    );
  };

  const handleCheck = (fridgeId: number, checked: boolean) => {
    setCheckedIds((ids) => (checked ? [...ids, fridgeId] : ids.filter((id) => id !== fridgeId)));
  };

  // 여러 재료를 연달아 처리할 수 있어 진행 중인 id를 모두 기록한다.
  // mutate의 콜백은 마지막 호출에만 실행되므로 호출마다 결과를 받는 mutateAsync를 쓴다.
  const removeRow = async (
    fridgeId: number,
    request: Promise<unknown>,
    successMessage: string,
  ): Promise<void> => {
    setRemovingIds((ids) => [...ids, fridgeId]);
    try {
      await request;
      setCheckedIds((ids) => ids.filter((id) => id !== fridgeId));
      void message.success(successMessage);
    } catch {
      // 실패 알림은 MutationCache.onError가 띄운다.
    } finally {
      setRemovingIds((ids) => ids.filter((id) => id !== fridgeId));
    }
  };

  const handleDiscard = (fridgeId: number) =>
    removeRow(
      fridgeId,
      // quantity를 빼고 보내면 남은 양 전부를 뺀다.
      takeOutMutation.mutateAsync({ fridgeId, request: { action: 'DISCARDED' } }),
      '버렸어요',
    );

  const handleDelete = (fridgeId: number) =>
    removeRow(
      fridgeId,
      removeMutation.mutateAsync({ fridgeId, reason: 'WRONG_ENTRY' }),
      '재료를 삭제했습니다.',
    );

  const renderRows = (items: FridgeItemResponse[]) => (
    <Flex vertical gap="xs">
      {items.map((item) => {
        const fridgeId = item.id ?? 0;
        return (
          <FridgeItemRow
            key={fridgeId}
            item={item}
            checked={checkedIds.includes(fridgeId)}
            removing={removingIds.includes(fridgeId)}
            highlighted={newFridgeIds.includes(fridgeId)}
            onCheck={(checked) => handleCheck(fridgeId, checked)}
            onEdit={() => setEditingItem(item)}
            onConsume={() => setConsumingItem(item)}
            onDiscard={() => handleDiscard(fridgeId)}
            onDelete={() => handleDelete(fridgeId)}
          />
        );
      })}
    </Flex>
  );

  const renderBody = () => {
    if (isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
    if (isError) {
      return (
        <Result
          status="error"
          title="냉장고 재료를 불러오지 못했어요."
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      );
    }
    if (data.length === 0) {
      return (
        <Empty description="냉장고가 비어 있어요">
          <Typography.Paragraph type="secondary">
            메인 화면의 [냉장고에 뭐가 남았나요?]에서 재료를 추가해 보세요.
          </Typography.Paragraph>
          <Button onClick={() => navigate(ROUTES.home)}>재료 추가하러 가기</Button>
        </Empty>
      );
    }

    const visibleItems = data.filter((item) => matchesFridgeTab(item, storage));
    if (visibleItems.length === 0) {
      return (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={EMPTY_TAB_MESSAGES[storage]} />
      );
    }
    if (sort !== 'EXPIRATION') return renderRows(visibleItems);

    return (
      <Flex vertical gap="middle">
        {EXPIRATION_GROUPS.map((group) => {
          const groupItems = visibleItems.filter(group.match);
          if (groupItems.length === 0) return null;
          return (
            <section key={group.label} aria-label={group.label}>
              <Typography.Text strong className={styles.groupTitle}>
                {group.label}{' '}
                <Typography.Text type="secondary">{groupItems.length}</Typography.Text>
              </Typography.Text>
              {renderRows(groupItems)}
            </section>
          );
        })}
      </Flex>
    );
  };

  const countOf = (tab: FridgeTab) =>
    data?.filter((item) => matchesFridgeTab(item, tab)).length ?? 0;
  const checkedItems = data?.filter((item) => checkedIds.includes(item.id ?? 0)) ?? [];

  return (
    <SectionCard
      title="재료 목록"
      extra={
        <Select
          value={sort}
          onChange={setSort}
          options={SORT_OPTIONS}
          aria-label="정렬"
          className={styles.sortSelect}
        />
      }
    >
      <Flex vertical gap="middle" className={styles.container}>
        <Segmented<FridgeTab>
          value={storage}
          onChange={handleStorageChange}
          options={FRIDGE_TABS.map((tab) => ({
            value: tab,
            label: `${TAB_LABELS[tab]} ${countOf(tab)}`,
          }))}
          className={styles.storageTabs}
        />

        {renderBody()}

        {checkedItems.length > 0 && (
          <Flex
            align="center"
            justify="space-between"
            wrap
            gap="small"
            className={styles.selectionBar}
          >
            <Typography.Text className={styles.selectionText}>
              <CheckOutlined /> {checkedItems.length}개 선택됨 ·{' '}
              {checkedItems.map((item) => item.rawName).join(', ')}
            </Typography.Text>
            {/* TODO: AI 상담 기능이 생기면 선택한 재료로 추천을 요청한다. */}
            <Button className={styles.selectionButton} onClick={openChat}>
              이 재료 위주로 AI 추천
            </Button>
          </Flex>
        )}
      </Flex>

      {editingItem && (
        <FridgeItemEditModal item={editingItem} onClose={() => setEditingItem(undefined)} />
      )}
      {consumingItem && (
        <FridgeItemConsumeModal item={consumingItem} onClose={() => setConsumingItem(undefined)} />
      )}
    </SectionCard>
  );
}
