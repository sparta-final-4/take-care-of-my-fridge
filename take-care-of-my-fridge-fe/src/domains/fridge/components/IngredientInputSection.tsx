import { CalendarOutlined, CloseOutlined, PlusOutlined } from '@ant-design/icons';
import { App, Button, DatePicker, Divider, Flex, Input, Select, Typography } from 'antd';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { createSearchParams, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { SectionCard } from '@/common/components/SectionCard';
import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';

import type { FridgeItemCreateRequest, StorageType } from '../api';
import { STORAGE_LABELS } from '../format';
import { MOCK_QUICK_CHIPS } from '../mocks';
import { getNewItemsView, type NewFridgeItemsState } from '../newItems';
import { useCreateFridgeItems } from '../queries';
import styles from './IngredientInputSection.module.scss';

// FridgeItemCreateRequest.java의 @Size 제한
const MAX_ITEMS = 20;
const MAX_NAME_LENGTH = 200;

const STORAGE_OPTIONS = (Object.keys(STORAGE_LABELS) as StorageType[]).map((value) => ({
  value,
  label: `보관: ${STORAGE_LABELS[value]}`,
}));

type StagedItem = FridgeItemCreateRequest['items'][number] & { key: string };

export function IngredientInputSection() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((state) => state.status === 'authenticated');
  const createMutation = useCreateFridgeItems();
  const openChat = useChatModalStore((state) => state.openChat);

  const [name, setName] = useState('');
  const [quantity, setQuantity] = useState('');
  const [expirationDate, setExpirationDate] = useState<Dayjs | null>(null);
  const [storage, setStorage] = useState<StorageType | undefined>('REFRIGERATED');
  const [stagedItems, setStagedItems] = useState<StagedItem[]>([]);

  const handleAdd = () => {
    const rawName = name.trim();
    const parsedQuantity = Number(quantity);
    if (!rawName) {
      void message.warning('재료명을 입력해 주세요.');
      return;
    }
    if (rawName.length > MAX_NAME_LENGTH) {
      void message.warning(`재료명은 ${MAX_NAME_LENGTH}자 이하여야 합니다.`);
      return;
    }
    if (!quantity.trim() || !Number.isFinite(parsedQuantity) || parsedQuantity <= 0) {
      void message.warning('수량은 0보다 큰 숫자로 입력해 주세요.');
      return;
    }
    if (stagedItems.length >= MAX_ITEMS) {
      void message.warning(`한 번에 최대 ${MAX_ITEMS}개까지만 등록할 수 있습니다.`);
      return;
    }

    setStagedItems((items) => [
      ...items,
      {
        key: crypto.randomUUID(),
        rawName,
        quantity: parsedQuantity,
        storage,
        expirationDate: expirationDate?.format('YYYY-MM-DD'),
      },
    ]);
    setName('');
    setQuantity('');
    setExpirationDate(null);
  };

  const handleRemove = (key: string) => {
    setStagedItems((items) => items.filter((item) => item.key !== key));
  };

  const handleSave = () => {
    if (!isAuthenticated) {
      void message.info('로그인하면 냉장고에 저장할 수 있어요.');
      return;
    }
    if (stagedItems.length === 0) {
      void message.warning('저장할 재료를 먼저 추가해 주세요.');
      return;
    }

    const items = stagedItems.map(({ rawName, quantity, storage, expirationDate }) => ({
      rawName,
      quantity,
      storage,
      expirationDate,
    }));
    createMutation.mutate(
      { items },
      {
        onSuccess: (createdItems) => {
          setStagedItems([]);
          const { ids, tab, hiddenSeasoningCount } = getNewItemsView(createdItems);
          void message.success(
            hiddenSeasoningCount > 0
              ? `냉장고에 저장했습니다. 기본 양념 ${hiddenSeasoningCount}개는 기본 양념 탭에 있어요.`
              : '냉장고에 저장했습니다.',
          );
          // 등록 직후에만 최신 등록순으로 열고 방금 넣은 재료를 강조한다.
          const search = createSearchParams({ sort: 'CREATED' });
          if (tab !== 'ALL') search.set('storage', tab);
          const state: NewFridgeItemsState = { newFridgeIds: ids };
          navigate({ pathname: ROUTES.fridge, search: search.toString() }, { state });
        },
      },
    );
  };

  return (
    <SectionCard title="냉장고에 뭐가 남았나요?">
      <div className={styles.container}>
        <div className={styles.formRow}>
          <Input
            placeholder="재료명 (예: 참치캔)"
            aria-label="재료명"
            value={name}
            maxLength={MAX_NAME_LENGTH}
            onChange={(event) => setName(event.target.value)}
            onPressEnter={handleAdd}
            className={styles.nameInput}
          />
          <Input
            placeholder="수량"
            inputMode="decimal"
            suffix={
              <Typography.Text type="secondary" className={styles.unitHint}>
                단위 자동
              </Typography.Text>
            }
            aria-label="수량"
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            onPressEnter={handleAdd}
            className={styles.quantityInput}
          />
          <DatePicker
            placeholder="소비기한"
            suffixIcon={<CalendarOutlined />}
            aria-label="소비기한"
            value={expirationDate}
            onChange={setExpirationDate}
            className={styles.datePicker}
          />
          <Select
            value={storage}
            onChange={setStorage}
            options={STORAGE_OPTIONS}
            placeholder="보관: 자동"
            allowClear
            aria-label="보관 방식"
            className={styles.storageSelect}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAdd}
            className={styles.addButton}
          >
            재료 추가
          </Button>
        </div>

        <Flex align="center" wrap gap="small" className={styles.quickRow}>
          <Typography.Text type="secondary" className={styles.quickLabel}>
            빠른 선택
          </Typography.Text>
          <Flex wrap gap="xs">
            {MOCK_QUICK_CHIPS.map((chip) => (
              <Button
                key={chip.id}
                size="small"
                shape="round"
                icon={<PlusOutlined />}
                onClick={() => setName(chip.name)}
                className={styles.chipButton}
              >
                {chip.name}
              </Button>
            ))}
          </Flex>
        </Flex>

        <Divider className={styles.divider} />

        <Flex vertical gap="xs" className={styles.stagedList}>
          {stagedItems.map((item) => (
            <div key={item.key} className={styles.stagedItem}>
              <Typography.Text strong className={styles.stagedName}>
                {item.rawName}
              </Typography.Text>
              <Typography.Text className={styles.stagedQty}>{item.quantity}</Typography.Text>
              <Typography.Text className={styles.stagedDate}>
                {item.expirationDate ?? '소비기한 자동'}
              </Typography.Text>
              <Typography.Text type="secondary" className={styles.stagedStorage}>
                {item.storage ? STORAGE_LABELS[item.storage] : '자동'}
              </Typography.Text>
              <Button
                type="text"
                size="small"
                icon={<CloseOutlined />}
                aria-label={`${item.rawName} 삭제`}
                onClick={() => handleRemove(item.key)}
                className={styles.removeButton}
              />
            </div>
          ))}
        </Flex>

        <Flex justify="flex-end" wrap gap="small" className={styles.footerActions}>
          <Button className={styles.actionButton} onClick={openChat}>
            AI 상담
          </Button>
          <Button
            type="primary"
            loading={createMutation.isPending}
            onClick={handleSave}
            className={styles.actionButton}
          >
            냉장고 저장
          </Button>
        </Flex>
      </div>
    </SectionCard>
  );
}
