import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Flex, Pagination, Result, Skeleton, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { generatePath, useNavigate, useSearchParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';
import { SectionLoading } from '@/common/components/SectionLoading';
import { formatPrice } from '@/domains/cart/format';
import { ORDER_STATUS_LABELS } from '@/domains/order/format';
import { orderQueries } from '@/domains/order/queries';

import styles from './OrderListSection.module.scss';

const PAGE_SIZE = 10;

// 결제 대기·결제 완료·구매 확정 주문을 최신 주문부터 보여 준다. 취소된 주문은 서버가 보내지 않는다.
// 결제 대기는 PG 결제 뒤 서버 확인을 기다리는 주문일 수 있어 보이기만 하고, 주문 확인 화면으로 들어가는 버튼은 두지 않는다.
// 페이지는 URL 쿼리에 두어 새로고침·뒤로 가기에도 유지한다.
export function OrderListSection() {
  const navigate = useNavigate();
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
    <SectionCard title="내 주문 내역" bracket={false}>
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
        <SectionLoading loading={isPlaceholderData}>
          <Flex vertical gap="large">
            <ul className={styles.list}>
              <li className={`${styles.row} ${styles.head}`} aria-hidden="true">
                <Typography.Text strong className={styles.orderNumber}>
                  주문번호
                </Typography.Text>
                <Typography.Text strong className={styles.date}>
                  주문일
                </Typography.Text>
                <Typography.Text strong className={styles.amount}>
                  결제 금액
                </Typography.Text>
                <Typography.Text strong className={styles.status}>
                  상태
                </Typography.Text>
              </li>
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
                    {order.status && ORDER_STATUS_LABELS[order.status]}
                  </Tag>
                  {order.status !== 'PENDING' && (
                    <Button
                      className={styles.action}
                      onClick={() =>
                        navigate(
                          generatePath(ROUTES.mypageOrderDetail, {
                            orderId: String(order.orderId),
                          }),
                        )
                      }
                    >
                      상세 보기
                    </Button>
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
        </SectionLoading>
      )}
    </SectionCard>
  );
}
