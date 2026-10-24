import { useQuery } from '@tanstack/react-query';
import { Button, Flex, InputNumber, Input, Result, Select, Table, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useSearchParams } from 'react-router';

import type {
  SaleCandidateProductResponse,
  SaleCandidateProductsRequest,
} from '@/common/api/clients';
import { formatPrice } from '@/domains/cart/format';
import { PRODUCT_TYPE_LABELS } from '@/domains/product/format';
import { MAX_DISCOUNT_RATE, MIN_DISCOUNT_RATE, salePriceOf } from '@/domains/sale/format';
import { saleQueries } from '@/domains/sale/queries';

import styles from './SaleProductPicker.module.scss';

const PAGE_SIZE = 10;
const ALL_TYPES = 'ALL';

export interface SaleProductChoice {
  productId: number;
  name: string;
  price: number;
  stock: number;
}

// 고른 상품과 그 행에서 입력한 값. 페이지를 넘겨도 남는다.
export interface SaleItemDraft {
  product: SaleProductChoice;
  discountRate: number | null;
  limitedQuantity: number | null;
}

// 응답 필드는 모두 선택값으로 생성돼, 표에 쓰는 값이 다 있는 행만 만든다.
interface PickerRow {
  productId: number;
  name: string;
  imageUrl?: string;
  productType?: SaleCandidateProductResponse['productType'];
  price: number;
  stock: number;
}

interface SaleProductPickerProps {
  // 특가는 여러 상품, 번개 드롭은 상품 하나를 고른다.
  multiple: boolean;
  // 번개 드롭만 한정 수량을 입력한다.
  withLimitedQuantity: boolean;
  drafts: SaleItemDraft[];
  onToggle: (choices: SaleProductChoice[], selected: boolean) => void;
  onChangeDraft: (productId: number, changes: Partial<SaleItemDraft>) => void;
}

export function SaleProductPicker({
  multiple,
  withLimitedQuantity,
  drafts,
  onToggle,
  onChangeDraft,
}: SaleProductPickerProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('q')?.trim() ?? '';
  const typeParam = searchParams.get('type');
  const type: SaleCandidateProductsRequest['type'] =
    typeParam === 'INGREDIENT' || typeParam === 'MEALKIT' ? typeParam : undefined;
  const page = Math.max(1, Number(searchParams.get('page')) || 1);

  // 값이 없으면 쿼리에서 지운다. 검색 조건이 바뀌면 첫 페이지로 돌아간다.
  const updateSearch = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(searchParams);
    if (!('page' in changes)) next.delete('page');
    Object.entries(changes).forEach(([key, value]) =>
      value ? next.set(key, value) : next.delete(key),
    );
    setSearchParams(next);
  };

  const products = useQuery(
    saleQueries.candidates({ keyword: keyword || undefined, type, page, size: PAGE_SIZE }),
  );
  const rows = (products.data?.content ?? []).flatMap<PickerRow>((product) => {
    const { productId, name, price, stock } = product;
    if (productId === undefined || name === undefined || price === undefined || stock === undefined)
      return [];
    return [
      {
        productId,
        name,
        price,
        stock,
        imageUrl: product.imageUrl,
        productType: product.productType,
      },
    ];
  });

  const draftOf = (row: PickerRow) =>
    drafts.find((draft) => draft.product.productId === row.productId);

  const toChoice = (row: PickerRow): SaleProductChoice => ({
    productId: row.productId,
    name: row.name,
    price: row.price,
    stock: row.stock,
  });

  const columns: TableColumnsType<PickerRow> = [
    {
      title: '상품',
      className: styles.nameColumn,
      key: 'name',
      render: (_, row) => (
        <Flex align="center" gap="small">
          {row.imageUrl ? (
            <img src={row.imageUrl} alt={row.name} className={styles.thumb} loading="lazy" />
          ) : (
            <span className={styles.thumbPlaceholder} aria-hidden="true" />
          )}
          <Typography.Text>{row.name}</Typography.Text>
        </Flex>
      ),
    },
    {
      title: '종류',
      dataIndex: 'productType',
      key: 'productType',
      render: (value: PickerRow['productType']) => (value ? PRODUCT_TYPE_LABELS[value] : '-'),
    },
    {
      title: '재고',
      dataIndex: 'stock',
      key: 'stock',
      align: 'right',
      render: (value: number) => value,
    },
    {
      title: '정가',
      dataIndex: 'price',
      key: 'price',
      align: 'right',
      render: (value: number) => formatPrice(value),
    },
    {
      title: '할인율(%)',
      key: 'discountRate',
      render: (_, row) => {
        const draft = draftOf(row);
        return (
          <InputNumber
            aria-label={`${row.name} 할인율`}
            min={MIN_DISCOUNT_RATE}
            max={MAX_DISCOUNT_RATE}
            precision={0}
            disabled={!draft}
            value={draft?.discountRate ?? null}
            onChange={(value) => onChangeDraft(row.productId, { discountRate: value })}
            className={styles.numberInput}
          />
        );
      },
    },
    {
      title: '할인가',
      key: 'salePrice',
      render: (_, row) => {
        const discountRate = draftOf(row)?.discountRate;
        return discountRate ? formatPrice(salePriceOf(row.price, discountRate)) : '-';
      },
    },
    ...(withLimitedQuantity
      ? [
          {
            title: '한정 수량',
            key: 'limitedQuantity',
            render: (_: unknown, row: PickerRow) => {
              const draft = draftOf(row);
              return (
                <Flex align="center" gap="small">
                  <InputNumber
                    aria-label={`${row.name} 한정 수량`}
                    min={1}
                    max={row.stock}
                    precision={0}
                    disabled={!draft}
                    value={draft?.limitedQuantity ?? null}
                    onChange={(value) => onChangeDraft(row.productId, { limitedQuantity: value })}
                    className={styles.numberInput}
                  />
                  <Typography.Text type="secondary">/ 재고 {row.stock}개</Typography.Text>
                </Flex>
              );
            },
          },
        ]
      : []),
  ];

  if (products.isError) {
    return (
      <Result
        status="error"
        title="상품 목록을 불러오지 못했습니다"
        extra={<Button onClick={() => void products.refetch()}>다시 시도</Button>}
      />
    );
  }

  return (
    <Flex vertical gap="middle">
      <Flex wrap gap="small">
        <Input.Search
          key={keyword}
          defaultValue={keyword}
          placeholder="상품 이름"
          aria-label="상품 검색"
          enterButton="검색"
          allowClear
          maxLength={50}
          onSearch={(value) => updateSearch({ q: value.trim() || undefined })}
          className={styles.search}
        />
        <Select
          aria-label="상품 종류"
          value={type ?? ALL_TYPES}
          options={[
            { value: ALL_TYPES, label: '전체' },
            { value: 'INGREDIENT', label: PRODUCT_TYPE_LABELS.INGREDIENT },
            { value: 'MEALKIT', label: PRODUCT_TYPE_LABELS.MEALKIT },
          ]}
          onChange={(value: string) =>
            updateSearch({ type: value === ALL_TYPES ? undefined : value })
          }
          className={styles.typeFilter}
        />
      </Flex>
      <Table<PickerRow>
        className={styles.table}
        rowKey="productId"
        loading={products.isPending}
        dataSource={rows}
        columns={columns}
        scroll={{ x: 'max-content' }}
        rowSelection={{
          type: multiple ? 'checkbox' : 'radio',
          selectedRowKeys: drafts.map((draft) => draft.product.productId),
          onSelect: (row, selected) => onToggle([toChoice(row)], selected),
          onSelectAll: (selected, _rows, changeRows) =>
            onToggle(changeRows.map(toChoice), selected),
        }}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: products.data?.totalElements,
          showSizeChanger: false,
          onChange: (next) => updateSearch({ page: next > 1 ? String(next) : undefined }),
        }}
        locale={{ emptyText: '조건에 맞는 상품이 없습니다.' }}
      />
    </Flex>
  );
}
