import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Checkbox, Form, Input, Space, Typography } from 'antd';
import { isAxiosError } from 'axios';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode, getErrorMessage } from '@/common/api/errors';
import { signUp, type SignUpRequest } from '@/domains/member/api';
import { memberQueries } from '@/domains/member/queries';

import styles from './SignUpPage.module.scss';

type FormValues = SignUpRequest & { passwordConfirm: string };

const tasteOptions = [
  { label: '짠맛', value: 'SALTY' },
  { label: '단맛', value: 'SWEET' },
  { label: '매운맛', value: 'SPICY' },
  { label: '감칠맛', value: 'UMAMI' },
  { label: '담백한 맛', value: 'MILD' },
];

export default function SignUpPage() {
  const [form] = Form.useForm<FormValues>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [checkedEmail, setCheckedEmail] = useState<string>();
  const [checkedNickname, setCheckedNickname] = useState<string>();
  const [availabilityError, setAvailabilityError] = useState<string>();
  const [checking, setChecking] = useState<'email' | 'nickname'>();
  const signupMutation = useMutation({ mutationFn: signUp, meta: { skipGlobalError: true } });

  const check = async (field: 'email' | 'nickname') => {
    setAvailabilityError(undefined);
    try {
      await form.validateFields([field]);
      const value = form.getFieldValue(field)?.trim() as string;
      setChecking(field);
      const available = await queryClient.fetchQuery(
        field === 'email'
          ? memberQueries.emailAvailability(value)
          : memberQueries.nicknameAvailability(value),
      );
      if (form.getFieldValue(field)?.trim() !== value) return;
      if (!available) {
        form.setFields([{ name: field, errors: ['이미 사용 중입니다.'] }]);
        return;
      }
      if (field === 'email') setCheckedEmail(value);
      else setCheckedNickname(value);
    } catch (error) {
      if (isAxiosError(error)) setAvailabilityError(getErrorMessage(error));
    } finally {
      setChecking(undefined);
    }
  };

  const submit = (values: FormValues) => {
    const nickname = values.nickname.trim();
    if (checkedEmail !== values.email) {
      form.setFields([{ name: 'email', errors: ['이메일 중복 확인을 해 주세요.'] }]);
      return;
    }
    if (checkedNickname !== nickname) {
      form.setFields([{ name: 'nickname', errors: ['닉네임 중복 확인을 해 주세요.'] }]);
      return;
    }
    signupMutation.mutate(
      { email: values.email, password: values.password, nickname, tastes: values.tastes ?? [] },
      {
        onSuccess: () => navigate(ROUTES.signupComplete, { state: { email: values.email } }),
        onError: (error) => {
          if (getErrorCode(error) === 'M001') {
            setCheckedEmail(undefined);
            form.setFields([{ name: 'email', errors: ['이미 가입된 이메일입니다.'] }]);
          }
          if (getErrorCode(error) === 'M002') {
            setCheckedNickname(undefined);
            form.setFields([{ name: 'nickname', errors: ['이미 사용 중인 닉네임입니다.'] }]);
          }
        },
      },
    );
  };

  return (
    <section className={styles.container}>
      <Typography.Title level={2}>회원가입</Typography.Title>
      <Typography.Paragraph type="secondary">
        계정을 만들고 냉장고를 시작해 보세요.
      </Typography.Paragraph>
      {availabilityError && (
        <Alert type="error" message={availabilityError} className={styles.error} />
      )}
      {signupMutation.error &&
        !['M001', 'M002'].includes(getErrorCode(signupMutation.error) ?? '') && (
          <Alert
            type="error"
            message={getErrorMessage(signupMutation.error)}
            className={styles.error}
          />
        )}
      <Form<FormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ tastes: [] }}
        onValuesChange={(changed) => {
          if ('email' in changed) setCheckedEmail(undefined);
          if ('nickname' in changed) setCheckedNickname(undefined);
        }}
        onFinish={submit}
      >
        <Form.Item
          label="이메일"
          name="email"
          rules={[
            { required: true, type: 'email', max: 255, message: '올바른 이메일을 입력해 주세요.' },
          ]}
        >
          <Input autoComplete="email" />
        </Form.Item>
        <Space className={styles.checkRow}>
          <Button onClick={() => void check('email')} loading={checking === 'email'}>
            이메일 중복 확인
          </Button>
          {checkedEmail && <Typography.Text type="success">사용할 수 있습니다.</Typography.Text>}
        </Space>
        <Form.Item
          label="닉네임"
          name="nickname"
          rules={[
            { required: true, max: 30, message: '닉네임을 입력해 주세요.' },
            {
              validator: (_, value: string) =>
                !value || value.trim()
                  ? Promise.resolve()
                  : Promise.reject(new Error('닉네임을 입력해 주세요.')),
            },
          ]}
        >
          <Input autoComplete="nickname" />
        </Form.Item>
        <Space className={styles.checkRow}>
          <Button onClick={() => void check('nickname')} loading={checking === 'nickname'}>
            닉네임 중복 확인
          </Button>
          {checkedNickname && <Typography.Text type="success">사용할 수 있습니다.</Typography.Text>}
        </Space>
        <Form.Item
          label="비밀번호"
          name="password"
          rules={[
            { required: true, message: '비밀번호를 입력해 주세요.' },
            { min: 8, max: 30 },
            {
              pattern: /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9])[\x21-\x7E]+$/,
              message: '영문, 숫자, 특수문자를 포함해 주세요.',
            },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          label="비밀번호 확인"
          name="passwordConfirm"
          dependencies={['password']}
          rules={[
            { required: true, message: '비밀번호를 다시 입력해 주세요.' },
            ({ getFieldValue }) => ({
              validator: (_, value) =>
                value === getFieldValue('password')
                  ? Promise.resolve()
                  : Promise.reject(new Error('비밀번호가 일치하지 않습니다.')),
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item label="좋아하는 맛" name="tastes">
          <Checkbox.Group options={tasteOptions} />
        </Form.Item>
        <Typography.Paragraph type="secondary">
          선택하지 않아도 가입할 수 있습니다.
        </Typography.Paragraph>
        <Button type="primary" htmlType="submit" block loading={signupMutation.isPending}>
          가입하기
        </Button>
      </Form>
      <Typography.Paragraph className={styles.loginLink}>
        이미 계정이 있나요? <Link to={ROUTES.login}>로그인</Link>
      </Typography.Paragraph>
    </section>
  );
}
