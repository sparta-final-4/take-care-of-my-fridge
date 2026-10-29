import { Flex, Typography } from 'antd';

import type { SaleResponse } from '@/common/api/clients';
import { formatPrice } from '@/domains/cart/format';

interface SalePriceProps {
  listPrice: number;
  sale?: SaleResponse;
  // 상품 카드와 상세의 가격 글자 크기가 달라 바깥에서 정한다.
  priceClassName?: string;
}

// 할인 중이면 할인율, 할인가, 취소선을 그은 정가를 나란히 보여 준다. 폭이 좁으면 정가만 아래로 내려간다.
export function SalePrice({ listPrice, sale, priceClassName }: SalePriceProps) {
  if (!sale) {
    return (
      <Typography.Text strong className={priceClassName}>
        {formatPrice(listPrice)}
      </Typography.Text>
    );
  }

  return (
    <Flex align="baseline" gap="small" wrap>
      <Typography.Text strong type="danger" className={priceClassName}>
        {sale.discountRate}%
      </Typography.Text>
      <Typography.Text strong className={priceClassName}>
        {formatPrice(sale.salePrice ?? listPrice)}
      </Typography.Text>
      <Typography.Text delete type="secondary">
        {formatPrice(listPrice)}
      </Typography.Text>
    </Flex>
  );
}
