import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  DatePicker,
  Drawer,
  Flex,
  Form,
  InputNumber,
  Space,
  Tabs,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';

import { formatPrice } from '@/domains/cart/format';
import { createFlashDrop, createSpecialSale } from '@/domains/sale/api';
import {
  SaleProductPicker,
  type SaleItemDraft,
  type SaleProductChoice,
} from '@/domains/sale/components/SaleProductPicker';
import { SaleEventListSection } from '@/domains/sale/components/SaleEventListSection';
import { MAX_DISCOUNT_RATE, MIN_DISCOUNT_RATE, salePriceOf } from '@/domains/sale/format';
import { saleQueries } from '@/domains/sale/queries';

import styles from './SaleEventCreateSection.module.scss';

type SaleKind = 'SPECIAL' | 'FLASH_DROP';

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm';
// 서버는 시각을 한국 시간으로 비교하므로 시간대 없는 형식으로 보낸다.
const REQUEST_TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

interface SaleEventFormValues {
  startsAt: Dayjs;
  endsAt: Dayjs;
}

const LIST_TAB = 'LIST';

const range = (count: number) => Array.from({ length: count }, (_, index) => index);

// minimum 이전의 날짜는 막는다.
const disabledDateBefore = (minimum: Dayjs) => (date: Dayjs) => date.isBefore(minimum, 'day');

// minimum과 같은 날에는 그 시각 이전(같은 분 포함)의 시·분을 막는다.
const disabledTimeBefore = (minimum: Dayjs) => (date: Dayjs | null) => {
  if (!date?.isSame(minimum, 'day')) return {};
  return {
    disabledHours: () => range(minimum.hour()),
    disabledMinutes: (hour: number) => (hour === minimum.hour() ? range(minimum.minute() + 1) : []),
  };
};

const KIND_LABELS: Record<SaleKind, string> = { SPECIAL: '특가', FLASH_DROP: '번개 드롭' };

export function SaleEventCreateSection() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<SaleEventFormValues>();
  const [kind, setKind] = useState<SaleKind>('SPECIAL');
  const [drafts, setDrafts] = useState<SaleItemDraft[]>([]);
  const [summaryOpen, setSummaryOpen] = useState(false);
  // 행사 목록 탭은 조회만 한다. 수정·종료 화면은 아직 없다.
  const [listTab, setListTab] = useState(false);
  const [bulkRate, setBulkRate] = useState<number | null>(null);
  const startsAt = Form.useWatch('startsAt', form);

  const flashDrop = kind === 'FLASH_DROP';
  const complete =
    drafts.length > 0 &&
    drafts.every(
      (draft) => draft.discountRate !== null && (!flashDrop || draft.limitedQuantity !== null),
    );

  const registration = useMutation({
    mutationFn: (values: SaleEventFormValues) => {
      const startsAt = values.startsAt.format(REQUEST_TIME_FORMAT);
      const endsAt = values.endsAt.format(REQUEST_TIME_FORMAT);
      if (flashDrop) {
        const [draft] = drafts;
        return createFlashDrop({
          startsAt,
          endsAt,
          productId: draft.product.productId,
          discountRate: draft.discountRate ?? 0,
          limitedQuantity: draft.limitedQuantity ?? 0,
        });
      }
      return createSpecialSale({
        startsAt,
        endsAt,
        items: drafts.map((draft) => ({
          productId: draft.product.productId,
          discountRate: draft.discountRate ?? 0,
        })),
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: saleQueries.all }),
  });

  const handleChangeTab = (next: string) => {
    setListTab(next === LIST_TAB);
    if (next === LIST_TAB) return;
    setKind(next as SaleKind);
    setDrafts([]);
  };

  const handleToggle = (choices: SaleProductChoice[], selected: boolean) => {
    const toDraft = (product: SaleProductChoice): SaleItemDraft => ({
      product,
      discountRate: null,
      limitedQuantity: null,
    });
    setDrafts((current) => {
      if (!selected) {
        const removed = new Set(choices.map((choice) => choice.productId));
        return current.filter((draft) => !removed.has(draft.product.productId));
      }
      // 번개 드롭은 상품 하나라서 새로 고르면 이전 선택을 바꾼다.
      if (flashDrop) return choices.slice(0, 1).map(toDraft);
      const kept = new Set(current.map((draft) => draft.product.productId));
      return [...current, ...choices.filter((choice) => !kept.has(choice.productId)).map(toDraft)];
    });
  };

  const handleChangeDraft = (productId: number, changes: Partial<SaleItemDraft>) =>
    setDrafts((current) =>
      current.map((draft) =>
        draft.product.productId === productId ? { ...draft, ...changes } : draft,
      ),
    );

  // 고른 상품 모두의 할인율을 한 값으로 맞춘다. 이미 입력한 값은 덮어쓴다.
  const handleApplyBulkRate = () =>
    setDrafts((current) => current.map((draft) => ({ ...draft, discountRate: bulkRate })));

  const handleFinish = (values: SaleEventFormValues) =>
    registration.mutate(values, {
      onSuccess: () => {
        void message.success(`${KIND_LABELS[kind]} 행사를 등록했습니다.`);
        form.resetFields();
        setBulkRate(null);
        setDrafts([]);
        setSummaryOpen(false);
      },
    });

  return (
    <>
      <Tabs
        activeKey={listTab ? LIST_TAB : kind}
        onChange={handleChangeTab}
        items={[
          { key: 'SPECIAL', label: KIND_LABELS.SPECIAL },
          { key: 'FLASH_DROP', label: KIND_LABELS.FLASH_DROP },
          { key: LIST_TAB, label: '행사 목록' },
        ]}
      />
      {listTab ? (
        <SaleEventListSection />
      ) : (
        <Form<SaleEventFormValues> form={form} layout="vertical" onFinish={handleFinish}>
          {/* 시작·종료는 범위 선택기가 좁은 화면에서 달력이 화면 밖으로 나가 따로 받는다. 선택 개수와 등록은 같은 줄 끝에 둬서 표를 오래 내리지 않아도 누를 수 있다. */}
          <div className={styles.period}>
            <Flex gap="middle" wrap align="flex-end">
              <Form.Item
                name="startsAt"
                label="시작 시각"
                rules={[
                  { required: true, message: '시작 시각을 정해 주세요.' },
                  {
                    validator: (_, value: Dayjs | undefined) =>
                      !value || value.isAfter(dayjs())
                        ? Promise.resolve()
                        : Promise.reject(new Error('시작 시각은 지금 이후여야 합니다.')),
                  },
                ]}
              >
                <DatePicker
                  showTime={{ format: 'HH:mm' }}
                  format={DATE_TIME_FORMAT}
                  needConfirm={false}
                  showNow={false}
                  disabledDate={disabledDateBefore(dayjs())}
                  disabledTime={disabledTimeBefore(dayjs())}
                  className={styles.dateTime}
                />
              </Form.Item>
              <Form.Item
                name="endsAt"
                label="종료 시각"
                dependencies={['startsAt']}
                rules={[
                  { required: true, message: '종료 시각을 정해 주세요.' },
                  ({ getFieldValue }) => ({
                    validator: (_, value: Dayjs | undefined) => {
                      const startsAt = getFieldValue('startsAt') as Dayjs | undefined;
                      return !value || !startsAt || value.isAfter(startsAt)
                        ? Promise.resolve()
                        : Promise.reject(new Error('종료 시각은 시작 시각보다 늦어야 합니다.'));
                    },
                  }),
                ]}
              >
                <DatePicker
                  showTime={{ format: 'HH:mm' }}
                  format={DATE_TIME_FORMAT}
                  needConfirm={false}
                  showNow={false}
                  disabledDate={disabledDateBefore(startsAt ?? dayjs())}
                  disabledTime={disabledTimeBefore(startsAt ?? dayjs())}
                  className={styles.dateTime}
                />
              </Form.Item>
              <Flex align="center" gap="small" wrap className={styles.actions}>
                {!flashDrop && (
                  <Space.Compact>
                    <InputNumber
                      aria-label="일괄 할인율"
                      placeholder="일괄 할인율(%)"
                      min={MIN_DISCOUNT_RATE}
                      max={MAX_DISCOUNT_RATE}
                      precision={0}
                      value={bulkRate}
                      onChange={setBulkRate}
                      className={styles.bulkRate}
                    />
                    <Button
                      disabled={drafts.length === 0 || bulkRate === null}
                      onClick={handleApplyBulkRate}
                    >
                      일괄 적용
                    </Button>
                  </Space.Compact>
                )}
                <Button disabled={drafts.length === 0} onClick={() => setSummaryOpen(true)}>
                  고른 상품 {drafts.length}개
                </Button>
                <Button
                  type="primary"
                  htmlType="submit"
                  disabled={!complete}
                  loading={registration.isPending}
                >
                  등록
                </Button>
              </Flex>
            </Flex>
            {drafts.length > 0 && !complete && (
              <Typography.Text type="secondary">
                {flashDrop
                  ? '고른 상품의 할인율과 한정 수량을 입력해 주세요.'
                  : '고른 상품의 할인율을 모두 입력해 주세요.'}
              </Typography.Text>
            )}
          </div>
          {/* 상품 표는 Form.Item 밖에 둔다. 안에 두면 antd가 선택 칸 너비를 100%로 덮는다. */}
          <Flex vertical gap="small" className={styles.block}>
            <Typography.Text strong>
              {flashDrop ? '번개 드롭 상품 (하나)' : '특가 상품 (여러 개 가능)'}
            </Typography.Text>
            <Typography.Text type="secondary">
              상품을 체크한 행에서 할인율{flashDrop ? '과 한정 수량' : ''}을 입력합니다. 판매 중이고
              재고가 있으며 예정·진행 중인 행사에 들지 않은 상품만 나옵니다.
            </Typography.Text>
            <SaleProductPicker
              multiple={!flashDrop}
              withLimitedQuantity={flashDrop}
              drafts={drafts}
              onToggle={handleToggle}
              onChangeDraft={handleChangeDraft}
            />
          </Flex>
        </Form>
      )}
      <Drawer
        title={`고른 상품 ${drafts.length}개`}
        open={summaryOpen}
        onClose={() => setSummaryOpen(false)}
      >
        <Flex vertical gap="middle">
          {drafts.map((draft) => (
            <Flex key={draft.product.productId} align="center" justify="space-between" gap="small">
              <Flex vertical>
                <Typography.Text strong>{draft.product.name}</Typography.Text>
                <Typography.Text type="secondary">
                  {formatPrice(draft.product.price)}
                  {draft.discountRate !== null &&
                    ` → ${formatPrice(salePriceOf(draft.product.price, draft.discountRate))} (${draft.discountRate}%)`}
                  {draft.limitedQuantity !== null &&
                    ` · ${draft.limitedQuantity}개 한정 (재고 ${draft.product.stock}개)`}
                </Typography.Text>
              </Flex>
              <Button onClick={() => handleToggle([draft.product], false)}>제외</Button>
            </Flex>
          ))}
        </Flex>
      </Drawer>
    </>
  );
}
