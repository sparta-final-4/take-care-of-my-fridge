import { Button, Result, Space } from 'antd';
import { useLocation, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';

export default function SignUpCompletePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const email = (location.state as { email?: string } | null)?.email;

  return (
    <Result
      status="success"
      title="회원가입이 완료되었습니다"
      subTitle={email ? `${email}로 가입했습니다. 로그인해 주세요.` : '로그인해 주세요.'}
      extra={
        <Space>
          <Button onClick={() => navigate(ROUTES.home)}>메인으로</Button>
          <Button type="primary" onClick={() => navigate(ROUTES.login)}>
            로그인
          </Button>
        </Space>
      }
    />
  );
}
