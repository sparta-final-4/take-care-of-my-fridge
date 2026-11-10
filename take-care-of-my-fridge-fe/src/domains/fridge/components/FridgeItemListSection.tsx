import { CheckOutlined } from '@ant-design/icons';
import { App, Button, Empty, Flex, Result, Segmented, Select, Skeleton, Typography } from 'antd';
import { useCallback, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router';

import { SectionCard } from '@/common/components/SectionCard';
import { useDesktopPointer } from '@/common/hooks/useScreens';
import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';
import { toChatSelectedIngredients } from '@/domains/chat/selectedIngredients';

import type { FridgeItemResponse, FridgeSortType } from '../api';
import { FRIDGE_TABS, isFridgeTab, matchesFridgeTab, type FridgeTab } from '../filter';
import { STORAGE_LABELS } from '../format';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { readNewFridgeIds } from '../newItems';
import { useFridgeItems, useRemoveFridgeItem, useTakeOutFridgeItem } from '../queries';
import { FridgeItemConsumeModal } from './FridgeItemConsumeModal';
import { FridgeItemEditModal } from './FridgeItemEditModal';
import { FridgeIllustration } from './FridgeIllustration';
import { FridgeItemRow } from './FridgeItemRow';
import { StorageIcon } from './StorageIcon';
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

// 묶음마다 처음에는 이만큼만 보여 주고 [더 보기]로 나머지를 편다.
const VISIBLE_ROW_COUNT = 8;

// 임박순 그룹. 서버가 준 status·dDay만으로 나눈다.
const EXPIRATION_GROUPS: Array<{ label: string; match: (item: FridgeItemResponse) => boolean }> = [
  { label: '기한 지남', match: (item) => item.status === 'EXPIRED' },
  { label: '오늘까지', match: (item) => item.status === 'IMMINENT' && item.dDay === 0 },
  { label: '3일 이내', match: (item) => item.status === 'IMMINENT' && (item.dDay ?? 0) > 0 },
  { label: '여유 있음', match: (item) => item.status === 'NORMAL' },
];

interface FridgeItemListSectionProps {
  // 빈 냉장고 안내의 [재료 추가]: 재료 추가 팝업을 연다.
  onAdd: () => void;
}

export function FridgeItemListSection({ onAdd }: FridgeItemListSectionProps) {
  const openChat = useChatModalStore((state) => state.openChat);
  const { message } = App.useApp();
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
  // [더 보기]로 편 묶음(임박순 묶음 이름, 다른 정렬은 'ALL')
  const [expandedGroups, setExpandedGroups] = useState<string[]>([]);
  const hoverActions = useDesktopPointer();

  // 기존 링크가 그대로 열리도록 키는 storage로 두고 밀키트·기본 양념 탭 값만 더했다.
  const storageParam = searchParams.get('storage');
  const storage: FridgeTab = isFridgeTab(storageParam) ? storageParam : 'ALL';
  // 재료 추가 팝업에서 방금 등록했을 때만 있다. 정렬·탭을 바꾸면 state 없이 이동해 사라진다.
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

  // 재료 칸(memo)에 넘기는 함수는 매번 새로 만들지 않고 고정한다(useCallback). 동작은 그대로다.
  const handleCheck = useCallback((fridgeId: number, checked: boolean) => {
    setCheckedIds((ids) => (checked ? [...ids, fridgeId] : ids.filter((id) => id !== fridgeId)));
  }, []);

  // 여러 재료를 연달아 처리할 수 있어 진행 중인 id를 모두 기록한다.
  // mutate의 콜백은 마지막 호출에만 실행되므로 호출마다 결과를 받는 mutateAsync를 쓴다.
  const removeRow = useCallback(
    async (fridgeId: number, request: Promise<unknown>, successMessage: string): Promise<void> => {
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
    },
    [message],
  );

  // mutateAsync는 TanStack Query가 같은 함수를 계속 돌려준다.
  const takeOut = takeOutMutation.mutateAsync;
  const remove = removeMutation.mutateAsync;

  const handleDiscard = useCallback(
    (fridgeId: number) =>
      removeRow(
        fridgeId,
        // quantity를 빼고 보내면 남은 양 전부를 뺀다.
        takeOut({ fridgeId, request: { action: 'DISCARDED' } }),
        '버렸어요',
      ),
    [removeRow, takeOut],
  );

  const handleDelete = useCallback(
    (fridgeId: number) =>
      removeRow(fridgeId, remove({ fridgeId, reason: 'WRONG_ENTRY' }), '재료를 삭제했습니다.'),
    [removeRow, remove],
  );

  const renderRows = (groupKey: string, items: FridgeItemResponse[]) => {
    const expanded = expandedGroups.includes(groupKey);
    const hiddenCount = expanded ? 0 : Math.max(items.length - VISIBLE_ROW_COUNT, 0);
    const shownItems = hiddenCount > 0 ? items.slice(0, VISIBLE_ROW_COUNT) : items;
    return (
      <>
        <div className={styles.list}>
          {shownItems.map((item) => {
            const fridgeId = item.id ?? 0;
            return (
              <FridgeItemRow
                key={fridgeId}
                item={item}
                checked={checkedIds.includes(fridgeId)}
                removing={removingIds.includes(fridgeId)}
                highlighted={newFridgeIds.includes(fridgeId)}
                hoverActions={hoverActions}
                onCheck={handleCheck}
                onEdit={setEditingItem}
                onConsume={setConsumingItem}
                onDiscard={handleDiscard}
                onDelete={handleDelete}
              />
            );
          })}
        </div>
        {hiddenCount > 0 && (
          <Button
            type="text"
            block
            onClick={() => setExpandedGroups((keys) => [...keys, groupKey])}
            className={styles.moreButton}
          >
            더 보기 ({hiddenCount}개)
          </Button>
        )}
      </>
    );
  };

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
        <Empty
          image={<FridgeIllustration mood="empty" className={styles.emptyIllustration} />}
          description="냉장고가 비어 있어요"
        >
          <Typography.Paragraph type="secondary">
            [재료 추가]로 남은 재료를 담아 보세요.
          </Typography.Paragraph>
          <Button onClick={onAdd}>재료 추가</Button>
        </Empty>
      );
    }

    const visibleItems = data.filter((item) => matchesFridgeTab(item, storage));
    if (visibleItems.length === 0) {
      return (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={EMPTY_TAB_MESSAGES[storage]} />
      );
    }
    if (sort !== 'EXPIRATION') return renderRows('ALL', visibleItems);

    return (
      <Flex vertical gap="large">
        {EXPIRATION_GROUPS.map((group) => {
          const groupItems = visibleItems.filter(group.match);
          if (groupItems.length === 0) return null;
          return (
            <section key={group.label} aria-label={group.label} className={styles.group}>
              <div className={styles.groupHead}>
                <span className={styles.groupTitle}>{group.label}</span>
                <span className={styles.groupCount}>{groupItems.length}</span>
              </div>
              {renderRows(group.label, groupItems)}
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
    <SectionCard title="재료 목록">
      <Flex vertical gap="middle" className={styles.container}>
        {/* 도구 줄: 보관 탭 + 정렬. 목록이 길어 스크롤해도 상단바 바로 아래에 붙어 있다. */}
        <div className={styles.toolbar}>
          <Segmented<FridgeTab>
            value={storage}
            onChange={handleStorageChange}
            options={FRIDGE_TABS.map((tab) => ({
              value: tab,
              // 보관 탭(냉장·냉동·상온)은 앞에 작은 아이콘
              label:
                tab === 'REFRIGERATED' || tab === 'FROZEN' || tab === 'ROOM_TEMP' ? (
                  <span className={styles.tabLabel}>
                    <StorageIcon storage={tab} />
                    {`${TAB_LABELS[tab]} ${countOf(tab)}`}
                  </span>
                ) : (
                  `${TAB_LABELS[tab]} ${countOf(tab)}`
                ),
            }))}
            className={styles.storageTabs}
          />
          <Select
            value={sort}
            onChange={setSort}
            options={SORT_OPTIONS}
            aria-label="정렬"
            className={styles.sortSelect}
          />
        </div>

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
            {/* 체크한 재료 중 추천에 쓸 수 있는 것만 넘긴다. 남는 게 없으면 선택 없이 연다. */}
            <Button
              className={styles.selectionButton}
              onClick={() => openChat(toChatSelectedIngredients(checkedItems))}
            >
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
