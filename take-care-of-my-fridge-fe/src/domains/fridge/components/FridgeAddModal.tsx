import { CalendarOutlined, CloseOutlined, PlusOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  ConfigProvider,
  DatePicker,
  Divider,
  Flex,
  Input,
  Modal,
  Select,
  Typography,
  type InputRef,
} from 'antd';
import type { Dayjs } from 'dayjs';
import { useRef, useState } from 'react';
import { createSearchParams, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';

import type { FridgeItemCreateRequest, StorageType } from '../api';
import { STORAGE_LABELS } from '../format';
import { MOCK_QUICK_CHIPS } from '../mocks';
import { getNewItemsView, type NewFridgeItemsState } from '../newItems';
import { COMPACT_FIELD_THEME } from '../fieldTheme';
import { useCreateFridgeItems } from '../queries';
import styles from './FridgeAddModal.module.scss';

// FridgeItemCreateRequest.java의 @Size 제한
const MAX_ITEMS = 20;
const MAX_NAME_LENGTH = 200;

const STORAGE_OPTIONS = (Object.keys(STORAGE_LABELS) as StorageType[]).map((value) => ({
  value,
  label: `보관: ${STORAGE_LABELS[value]}`,
}));

type StagedItem = FridgeItemCreateRequest['items'][number] & { key: string };

interface FridgeAddModalProps {
  open: boolean;
  onClose: () => void;
}

// 재료 추가 팝업: [재료 이름] → [빠른 선택] → [수량 · 소비기한 · 보관] → [목록에 담기] → 담은 재료 → [냉장고에 저장]
// 닫았다 다시 열어도 담은 재료는 남아 있고, 저장하면 비운다.
export function FridgeAddModal({ open, onClose }: FridgeAddModalProps) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((state) => state.status === 'authenticated');
  const createMutation = useCreateFridgeItems();

  const [name, setName] = useState('');
  const [quantity, setQuantity] = useState('');
  const [expirationDate, setExpirationDate] = useState<Dayjs | null>(null);
  const [storage, setStorage] = useState<StorageType | undefined>('REFRIGERATED');
  const [stagedItems, setStagedItems] = useState<StagedItem[]>([]);
  const nameInputRef = useRef<InputRef>(null);

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
    // 다음 재료를 바로 적을 수 있게 이름 칸으로 돌아간다.
    nameInputRef.current?.focus();
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
      void message.warning('저장할 재료를 먼저 목록에 담아 주세요.');
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
          onClose();
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
    <Modal
      open={open}
      centered
      title="재료 추가"
      onCancel={onClose}
      // 열리면 바로 재료 이름을 적을 수 있게 이름 칸에 포커스한다.
      afterOpenChange={(opened) => {
        if (opened) nameInputRef.current?.focus();
      }}
      footer={
        <Button
          type="primary"
          size="large"
          block
          loading={createMutation.isPending}
          disabled={stagedItems.length === 0}
          onClick={handleSave}
        >
          {stagedItems.length > 0 ? `냉장고에 저장 (${stagedItems.length}개)` : '냉장고에 저장'}
        </Button>
      }
    >
      <ConfigProvider theme={COMPACT_FIELD_THEME}>
        <div className={styles.container}>
          <Input
            ref={nameInputRef}
            placeholder="재료명 (예: 참치캔)"
            aria-label="재료명"
            value={name}
            maxLength={MAX_NAME_LENGTH}
            onChange={(event) => setName(event.target.value)}
            onPressEnter={handleAdd}
          />

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

          <div className={styles.formRow}>
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
            />
            <DatePicker
              placeholder="소비기한"
              suffixIcon={<CalendarOutlined />}
              aria-label="소비기한"
              value={expirationDate}
              onChange={setExpirationDate}
              className={styles.field}
            />
            <Select
              value={storage}
              onChange={setStorage}
              options={STORAGE_OPTIONS}
              placeholder="보관: 자동"
              allowClear
              aria-label="보관 방식"
              className={`${styles.field} ${styles.storageSelect}`}
            />
          </div>

          <Button icon={<PlusOutlined />} block onClick={handleAdd}>
            목록에 담기
          </Button>

          <Divider className={styles.divider} />

          {/* 담은 재료: "두부 1 · 11/10 · 냉장 ×" 칩으로 나열 */}
          <Flex wrap gap="small" aria-label="담은 재료" className={styles.stagedList}>
            {stagedItems.length === 0 && (
              <Typography.Text type="secondary" className={styles.stagedEmpty}>
                목록에 담은 재료가 여기에 보여요
              </Typography.Text>
            )}
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
                  aria-label={`${item.rawName} 빼기`}
                  onClick={() => handleRemove(item.key)}
                  className={styles.removeButton}
                />
              </div>
            ))}
          </Flex>
        </div>
      </ConfigProvider>
    </Modal>
  );
}
