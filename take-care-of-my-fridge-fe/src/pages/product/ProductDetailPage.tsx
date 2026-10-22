import { Button, Result } from 'antd';
import { useNavigate, useParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import { ProductDetailSection } from '@/domains/product/components/ProductDetailSection';

export default function ProductDetailPage() {
  const navigate = useNavigate();
  const productId = Number(useParams().productId);

  if (!Number.isInteger(productId) || productId <= 0) {
    return (
      <Result
        status="404"
        title="상품을 찾을 수 없습니다"
        extra={<Button onClick={() => navigate(ROUTES.products)}>목록으로</Button>}
      />
    );
  }
  // 다른 상품으로 이동하면 수량 입력을 처음부터 시작한다.
  return <ProductDetailSection key={productId} productId={productId} />;
}
