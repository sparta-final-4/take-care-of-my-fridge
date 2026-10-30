import { useMutation } from '@tanstack/react-query';
import { Alert, Button, Form, Input, Typography } from 'antd';
import { isAxiosError } from 'axios';
import { Link, useNavigate, useSearchParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode, getErrorMessage } from '@/common/api/errors';
import type { LoginRequest, MyInfoResponse } from '@/common/api/clients';
import { setSession } from '@/common/auth/session';
import { login } from '@/domains/auth/api';

import styles from './LoginPage.module.scss';

function safeRedirect(value: string | null, role: MyInfoResponse['role']) {
  const fallback = role === 'ADMIN' ? ROUTES.adminMembers : ROUTES.home;
  if (!value) return fallback;
  const url = new URL(value, window.location.origin);
  return url.origin === window.location.origin && url.pathname !== ROUTES.login
    ? `${url.pathname}${url.search}${url.hash}`
    : fallback;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const loginMutation = useMutation({ mutationFn: login, meta: { skipGlobalError: true } });

  const handleSubmit = (values: LoginRequest) => {
    loginMutation.mutate(values, {
      onSuccess: async (member) => {
        await setSession(member);
        navigate(safeRedirect(searchParams.get('redirect'), member.role), { replace: true });
      },
    });
  };

  const error = loginMutation.error;
  const sessionReason = searchParams.get('reason');
  const isSuspended = getErrorCode(error) === 'A002';
  const errorMessage = isSuspended
    ? '정지된 계정입니다. 관리자에게 문의해 주세요.'
    : isAxiosError(error) && error.response?.status === 401
      ? '이메일 또는 비밀번호를 확인해 주세요.'
      : error
        ? getErrorMessage(error)
        : null;

  return (
    <section className={styles.container}>
      <Typography.Title level={2}>로그인</Typography.Title>
      <Typography.Paragraph type="secondary">
        냉장고에 남은 재료와 나에게 맞는 메뉴를 확인해 보세요.
      </Typography.Paragraph>
      {errorMessage && (
        <Alert type="error" message={errorMessage} showIcon className={styles.error} />
      )}
      {!errorMessage && sessionReason && (
        <Alert
          type="warning"
          message={
            sessionReason === 'suspended'
              ? '계정 이용이 제한되어 로그인이 해제되었습니다.'
              : '로그인이 만료되었습니다. 다시 로그인해 주세요.'
          }
          showIcon
          className={styles.error}
        />
      )}
      <Form<LoginRequest> layout="vertical" onFinish={handleSubmit} requiredMark={false}>
        <Form.Item
          label="이메일"
          name="email"
          rules={[
            { required: true, message: '이메일을 입력해 주세요.' },
            { type: 'email', message: '올바른 이메일 형식으로 입력해 주세요.' },
          ]}
        >
          <Input autoComplete="email" size="large" />
        </Form.Item>
        <Form.Item
          label="비밀번호"
          name="password"
          rules={[{ required: true, message: '비밀번호를 입력해 주세요.' }]}
        >
          <Input.Password autoComplete="current-password" size="large" />
        </Form.Item>
        <Button
          type="primary"
          htmlType="submit"
          size="large"
          block
          loading={loginMutation.isPending}
        >
          로그인
        </Button>
      </Form>
      <Typography.Paragraph className={styles.signupLink}>
        아직 계정이 없나요? <Link to={ROUTES.signup}>회원가입</Link>
      </Typography.Paragraph>
    </section>
  );
}
