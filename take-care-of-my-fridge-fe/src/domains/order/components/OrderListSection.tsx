import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Flex, Pagination, Result, Skeleton, Tag, Tooltip, Typography } from 'antd';
import dayjs from 'dayjs';
import { useSearchParams } from 'react-router';

import type { OrderSummaryResponse } from '@/common/api/clients';
import { SectionCard } from '@/common/components/SectionCard';
import { formatPrice } from '@/domains/cart/format';
import { orderQueries } from '@/domains/order/queries';

import styles from './OrderListSection.module.scss';

const PAGE_SIZE = 10;

const STATUS_LABELS: Partial<Record<NonNullable<OrderSummaryResponse['status']>, string>> = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  CONFIRMED: '구매 확정',
};

// 결제 대기·결제 완료·구매 확정 주문을 최신 주문부터 보여 준다. 취소된 주문은 서버가 보내지 않는다.
// 결제 대기는 PG 결제 뒤 서버 확인을 기다리는 주문일 수 있어 보이기만 하고, 주문 확인 화면으로 들어가는 버튼은 두지 않는다.
// 페이지는 URL 쿼리에 두어 새로고침·뒤로 가기에도 유지한다.
export function OrderListSection() {
  const [searchParams, setSearchParams] = useSearchParams();
  const pageParam = Number(searchParams.get('page'));
  const page = Number.isInteger(pageParam) && pageParam > 0 ? pageParam : 1;
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    orderQueries.list({ page, size: PAGE_SIZE }),
  );

  const handleChangePage = (nextPage: number) => {
    setSearchParams(nextPage === 1 ? {} : { page: String(nextPage) });
    window.scrollTo({ top: 0 });
  };

  const orders = data?.content ?? [];

  return (
    <SectionCard title="내 주문 내역">
      {isPending ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : isError ? (
        <Result
          status="error"
          title="주문 내역을 불러오지 못했습니다."
          extra={<Button onClick={() => refetch()}>다시 시도</Button>}
        />
      ) : orders.length === 0 ? (
        <Empty description="주문 내역이 없습니다." />
      ) : (
        <Flex vertical gap="large" className={isPlaceholderData ? styles.loading : undefined}>
          <ul className={styles.list}>
            {orders.map((order) => (
              <li key={order.orderId} className={styles.row}>
                <Typography.Text strong className={styles.orderNumber}>
                  {order.orderNumber}
                </Typography.Text>
                <Typography.Text type="secondary" className={styles.date}>
                  {order.orderedAt ? dayjs(order.orderedAt).format('YYYY-MM-DD') : '-'}
                </Typography.Text>
                <Typography.Text strong className={styles.amount}>
                  {formatPrice(order.payAmount ?? 0)}
                </Typography.Text>
                <Tag
                  color={order.status === 'PAID' ? 'processing' : undefined}
                  className={styles.status}
                >
                  {(order.status && STATUS_LABELS[order.status]) ?? order.status}
                </Tag>
                {order.status !== 'PENDING' && (
                  // 주문 상세 화면이 생기면 연결한다.
                  <Tooltip title="주문 상세 기능은 준비 중입니다.">
                    <Button className={styles.action} disabled>
                      상세 보기
                    </Button>
                  </Tooltip>
                )}
              </li>
            ))}
          </ul>
          <Pagination
            align="center"
            current={page}
            pageSize={PAGE_SIZE}
            total={data?.totalElements ?? 0}
            showSizeChanger={false}
            onChange={handleChangePage}
          />
        </Flex>
      )}
    </SectionCard>
  );
}
