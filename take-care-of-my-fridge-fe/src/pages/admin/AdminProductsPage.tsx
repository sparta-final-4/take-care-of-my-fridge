import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Flex,
  Input,
  Popconfirm,
  Result,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { generatePath, useNavigate, useSearchParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { AdminProductListRequest, ProductListItemResponse } from '@/common/api/clients';
import { deleteProduct } from '@/domains/product/api';
import { productQueries } from '@/domains/product/queries';

import styles from './AdminProductsPage.module.scss';

const PAGE_SIZE = 20;
const TYPE_LABELS = { INGREDIENT: '재료', MEALKIT: '밀키트' };
const STATUS_LABELS = { ON_SALE: '판매 중', SOLD_OUT: '품절', STOPPED: '판매 중지' };

export default function AdminProductsPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('keyword') ?? '';
  const typeParam = searchParams.get('type');
  const statusParam = searchParams.get('status');
  const type: AdminProductListRequest['type'] =
    typeParam === 'INGREDIENT' || typeParam === 'MEALKIT' ? typeParam : undefined;
  const status: AdminProductListRequest['status'] =
    statusParam === 'ON_SALE' || statusParam === 'SOLD_OUT' || statusParam === 'STOPPED'
      ? statusParam
      : undefined;
  const page = Math.max(1, Number(searchParams.get('page')) || 1);

  const updateSearch = (changes: Record<string, string | undefined>) =>
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      if (!('page' in changes)) next.delete('page');
      Object.entries(changes).forEach(([key, value]) =>
        value ? next.set(key, value) : next.delete(key),
      );
      return next;
    });

  const { data, isPending, isError, refetch } = useQuery(
    productQueries.adminList({
      keyword: keyword || undefined,
      type,
      status,
      page,
      size: PAGE_SIZE,
    }),
  );
  const deletion = useMutation({
    mutationFn: deleteProduct,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: productQueries.all }),
  });

  return (
    <section className={styles.container}>
      <Flex align="center" justify="space-between" wrap gap="middle" className={styles.heading}>
        <Typography.Title level={2}>상품 관리</Typography.Title>
        <Button type="primary" onClick={() => navigate(ROUTES.adminProductNew)}>
          상품 등록
        </Button>
      </Flex>
      <Space wrap className={styles.filters}>
        <Input.Search
          key={keyword}
          defaultValue={keyword}
          aria-label="상품명 검색"
          placeholder="상품명"
          maxLength={50}
          allowClear
          enterButton="검색"
          onSearch={(value) => updateSearch({ keyword: value.trim() || undefined })}
        />
        <Select
          aria-label="상품 종류 필터"
          placeholder="전체 종류"
          value={type}
          allowClear
          options={Object.entries(TYPE_LABELS).map(([value, label]) => ({ value, label }))}
          onChange={(value) => updateSearch({ type: value })}
          className={styles.filter}
        />
        <Select
          aria-label="판매 상태 필터"
          placeholder="전체 상태"
          value={status}
          allowClear
          options={Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }))}
          onChange={(value) => updateSearch({ status: value })}
          className={styles.filter}
        />
      </Space>
      {isError ? (
        <Result
          status="error"
          title="상품 목록을 불러오지 못했습니다"
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      ) : (
        <Table<ProductListItemResponse>
          rowKey="productId"
          loading={isPending}
          dataSource={data?.content}
          scroll={{ x: 'max-content' }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: data?.totalElements,
            showSizeChanger: false,
            onChange: (next) => updateSearch({ page: next > 1 ? String(next) : undefined }),
          }}
          locale={{ emptyText: '조건에 맞는 상품이 없습니다.' }}
          columns={[
            {
              title: '상품명',
              dataIndex: 'name',
              key: 'name',
              render: (value: string | undefined, product) => (
                <Button
                  type="link"
                  disabled={product.productId === undefined}
                  onClick={() => {
                    if (product.productId === undefined) return;
                    navigate(
                      generatePath(ROUTES.adminProductDetail, {
                        productId: String(product.productId),
                      }),
                    );
                  }}
                >
                  {value ?? '-'}
                </Button>
              ),
            },
            {
              title: '종류',
              dataIndex: 'productType',
              key: 'productType',
              render: (value: ProductListItemResponse['productType']) =>
                value ? TYPE_LABELS[value] : '-',
            },
            {
              title: '정상가',
              dataIndex: 'price',
              key: 'price',
              render: (value: number | undefined) =>
                value === undefined ? '-' : `${value.toLocaleString('ko-KR')}원`,
            },
            {
              title: '상태',
              dataIndex: 'status',
              key: 'status',
              render: (value: ProductListItemResponse['status']) =>
                value ? (
                  <Tag color={value === 'STOPPED' ? 'default' : 'blue'}>{STATUS_LABELS[value]}</Tag>
                ) : (
                  '-'
                ),
            },
            {
              title: '관리',
              key: 'actions',
              render: (_, product) => (
                <Popconfirm
                  title="판매를 중지하시겠습니까?"
                  description="상품은 목록에 남고 고객에게는 표시되지 않습니다."
                  okText="판매 중지"
                  cancelText="취소"
                  okButtonProps={{
                    danger: true,
                    loading: deletion.isPending && deletion.variables === product.productId,
                  }}
                  onConfirm={() => {
                    if (product.productId === undefined) return;
                    deletion.mutate(product.productId, {
                      onSuccess: () => void message.success('판매를 중지했습니다.'),
                    });
                  }}
                >
                  <Button
                    danger
                    disabled={product.status === 'STOPPED' || product.productId === undefined}
                  >
                    판매 중지
                  </Button>
                </Popconfirm>
              ),
            },
          ]}
        />
      )}
    </section>
  );
}
