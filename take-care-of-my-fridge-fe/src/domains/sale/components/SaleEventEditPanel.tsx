import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Flex,
  Form,
  InputNumber,
  Result,
  Skeleton,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Fragment } from 'react';

import type { SaleEventDetailResponse, SaleEventUpdateRequest } from '@/common/api/clients';
import { formatPrice } from '@/domains/cart/format';
import { updateSaleEvent } from '@/domains/sale/api';
import {
  DATE_TIME_FORMAT,
  disabledDateBefore,
  disabledTimeBefore,
  MAX_DISCOUNT_RATE,
  MIN_DISCOUNT_RATE,
  REQUEST_TIME_FORMAT,
  salePriceOf,
} from '@/domains/sale/format';
import { saleQueries } from '@/domains/sale/queries';

import styles from './SaleEventEditPanel.module.scss';

interface ItemValues {
  discountRate: number;
  limitedQuantity?: number;
}

interface FormValues {
  startsAt: Dayjs;
  endsAt: Dayjs;
  items: ItemValues[];
}

// 고치지 않는 값(상품 id·이름·정가)과 고치는 값의 처음 값을 한 줄로 모은다.
// 서버가 항목 값 중 화면에 쓰는 것이 다 있는 행사 항목만 만든다.
interface ItemRow extends ItemValues {
  productId: number;
  productName: string;
  listPrice: number;
}

const toRows = (event: SaleEventDetailResponse): ItemRow[] =>
  (event.items ?? []).flatMap((item) => {
    const { productId, productName, listPrice, discountRate } = item;
    if (
      productId === undefined ||
      productName === undefined ||
      listPrice === undefined ||
      discountRate === undefined
    )
      return [];
    return [
      { productId, productName, listPrice, discountRate, limitedQuantity: item.limitedQuantity },
    ];
  });

// 정가와 입력 중인 할인율로 계산한 할인가
function PriceText({ row, rate, className }: { row: ItemRow; rate?: number; className?: string }) {
  return (
    <Typography.Text type="secondary" className={className}>
      정가 {formatPrice(row.listPrice)}
      {rate !== null && rate !== undefined && (
        <> → 할인가 {formatPrice(salePriceOf(row.listPrice, rate))}</>
      )}
    </Typography.Text>
  );
}

interface EditFormProps {
  event: SaleEventDetailResponse;
  submitting: boolean;
  onSubmit: (request: SaleEventUpdateRequest) => void;
  onCancel: () => void;
}

// 행사 하나를 열 때마다 새로 만든다. 폼 값이 이전 행사의 값을 이어받지 않게 하려는 것이다.
function SaleEventEditForm({ event, submitting, onSubmit, onCancel }: EditFormProps) {
  const [form] = Form.useForm<FormValues>();
  const startsAt = Form.useWatch('startsAt', form);
  const items = Form.useWatch('items', form);
  const rows = toRows(event);
  const flashDrop = event.type === 'FLASH_DROP';

  // 상품 id는 화면에서 고치지 않아 폼 값에 없다. 서버에서 받은 항목의 순서대로 짝지어 보낸다.
  const handleFinish = (values: FormValues) =>
    onSubmit({
      startsAt: values.startsAt.format(REQUEST_TIME_FORMAT),
      endsAt: values.endsAt.format(REQUEST_TIME_FORMAT),
      items: values.items.map(({ discountRate, limitedQuantity }, index) => ({
        productId: rows[index].productId,
        discountRate,
        limitedQuantity,
      })),
    });

  return (
    <Form<FormValues>
      form={form}
      layout="vertical"
      requiredMark={false}
      initialValues={{
        startsAt: dayjs(event.startsAt),
        endsAt: dayjs(event.endsAt),
        items: rows,
      }}
      onFinish={handleFinish}
    >
      {/* 지금은 상품마다 행사를 따로 만들지만, 예전에 묶어 등록한 행사는 상품이 여럿이고 시각을 같이 쓴다. */}
      {rows.length > 1 && (
        <Alert
          type="info"
          showIcon
          title={`이 행사의 상품 ${rows.length}개가 같은 시각을 씁니다. 시각을 바꾸면 모두 바뀝니다.`}
          className={styles.notice}
        />
      )}
      <Flex wrap align="flex-end" className={styles.fields}>
        <Form.List name="items">
          {(fields) =>
            fields.map((field) => {
              const row = rows[field.name];
              const inputs = (
                <>
                  <Form.Item
                    name={[field.name, 'discountRate']}
                    label="할인율(%)"
                    rules={[{ required: true, message: '할인율을 입력해 주세요.' }]}
                  >
                    <InputNumber
                      min={MIN_DISCOUNT_RATE}
                      max={MAX_DISCOUNT_RATE}
                      precision={0}
                      className={styles.number}
                    />
                  </Form.Item>
                  {flashDrop && (
                    <Form.Item
                      name={[field.name, 'limitedQuantity']}
                      label="한정 수량"
                      rules={[{ required: true, message: '한정 수량을 입력해 주세요.' }]}
                    >
                      <InputNumber min={1} precision={0} className={styles.number} />
                    </Form.Item>
                  )}
                </>
              );
              // 항목이 하나면 입력칸 바로 뒤에 가격을 두고 시각이 이어진다.
              if (rows.length === 1) {
                return (
                  <Fragment key={field.key}>
                    {inputs}
                    <PriceText
                      row={row}
                      rate={items?.[field.name]?.discountRate}
                      className={styles.price}
                    />
                  </Fragment>
                );
              }
              return (
                <div key={field.key} className={styles.item}>
                  <Flex gap="small" align="baseline" className={styles.itemHead}>
                    <strong>{row.productName}</strong>
                    <PriceText row={row} rate={items?.[field.name]?.discountRate} />
                  </Flex>
                  <Flex wrap gap="middle" align="flex-end">
                    {inputs}
                  </Flex>
                </div>
              );
            })
          }
        </Form.List>
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
                const start = getFieldValue('startsAt') as Dayjs | undefined;
                return !value || !start || value.isAfter(start)
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
        <Flex gap="small" className={styles.actions}>
          <Button onClick={onCancel}>취소</Button>
          <Button type="primary" htmlType="submit" loading={submitting}>
            저장
          </Button>
        </Flex>
      </Flex>
    </Form>
  );
}

interface SaleEventEditPanelProps {
  saleEventId: number;
  onClose: () => void;
}

// 목록 행 아래로 펼쳐지는 행사 수정. 시작 전인 행사의 시각과 행사 항목을 한 번에 고친다. 상품을 넣거나 빼지는 않는다.
export function SaleEventEditPanel({ saleEventId, onClose }: SaleEventEditPanelProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const event = useQuery(saleQueries.event(saleEventId));

  const update = useMutation({
    mutationFn: (request: SaleEventUpdateRequest) => updateSaleEvent(saleEventId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: saleQueries.all }),
  });

  const handleSubmit = (request: SaleEventUpdateRequest) =>
    update.mutate(request, {
      onSuccess: () => {
        void message.success('행사를 수정했습니다.');
        onClose();
      },
    });

  if (event.isError) {
    return (
      <Result
        status="error"
        title="행사를 불러오지 못했습니다"
        extra={<Button onClick={() => void event.refetch()}>다시 시도</Button>}
      />
    );
  }
  if (!event.data) return <Skeleton active />;
  return (
    <SaleEventEditForm
      key={saleEventId}
      event={event.data}
      submitting={update.isPending}
      onSubmit={handleSubmit}
      onCancel={onClose}
    />
  );
}
