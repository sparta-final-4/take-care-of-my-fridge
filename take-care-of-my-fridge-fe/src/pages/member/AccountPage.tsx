import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Checkbox, Form, Input, Result, Space, Spin, Typography } from 'antd';
import { isAxiosError } from 'axios';
import { useState } from 'react';

import { useAuthStore } from '@/common/auth/authStore';
import { getErrorCode, getErrorMessage } from '@/common/api/errors';
import {
  changeNickname,
  changePassword,
  changeTastes,
  type ChangePasswordRequest,
  type MyInfoResponse,
} from '@/domains/member/api';
import { memberQueries } from '@/domains/member/queries';

import styles from './AccountPage.module.scss';

const tasteOptions = [
  { label: '짠맛', value: 'SALTY' },
  { label: '단맛', value: 'SWEET' },
  { label: '매운맛', value: 'SPICY' },
  { label: '감칠맛', value: 'UMAMI' },
  { label: '담백한 맛', value: 'MILD' },
];

function AccountForms({ member }: { member: MyInfoResponse }) {
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const [nicknameForm] = Form.useForm<{ nickname: string }>();
  const [passwordForm] = Form.useForm<ChangePasswordRequest & { confirm: string }>();
  const [checkedNickname, setCheckedNickname] = useState<string>();
  const [checking, setChecking] = useState(false);
  const [tastes, setTastes] = useState(member.tastes);

  const saved = async () => {
    await queryClient.invalidateQueries({ queryKey: memberQueries.me().queryKey });
    void message.success('저장했습니다.');
  };
  const nicknameMutation = useMutation({
    mutationFn: changeNickname,
    onSuccess: saved,
    meta: { skipGlobalError: true },
  });
  const tastesMutation = useMutation({ mutationFn: changeTastes, onSuccess: saved });
  const passwordMutation = useMutation({
    mutationFn: changePassword,
    meta: { skipGlobalError: true },
  });

  const checkNickname = async () => {
    try {
      await nicknameForm.validateFields();
      const nickname = nicknameForm.getFieldValue('nickname')?.trim() as string;
      setChecking(true);
      const available = await queryClient.fetchQuery(memberQueries.nicknameAvailability(nickname));
      if (nicknameForm.getFieldValue('nickname')?.trim() !== nickname) return;
      if (!available) {
        nicknameForm.setFields([{ name: 'nickname', errors: ['이미 사용 중입니다.'] }]);
        return;
      }
      setCheckedNickname(nickname);
    } catch (error) {
      if (isAxiosError(error)) void message.error(getErrorMessage(error));
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className={styles.cards}>
      <Card title="내 정보">
        <Typography.Paragraph>이메일: {member.email}</Typography.Paragraph>
        <Typography.Paragraph>가입일: {member.createdAt.slice(0, 10)}</Typography.Paragraph>
        <Typography.Paragraph>
          역할: {member.role === 'ADMIN' ? '관리자' : '일반 회원'}
        </Typography.Paragraph>
      </Card>
      <Card title="닉네임 변경">
        <Form
          form={nicknameForm}
          layout="vertical"
          initialValues={{ nickname: member.nickname }}
          onValuesChange={() => setCheckedNickname(undefined)}
          onFinish={({ nickname }) => {
            const value = nickname.trim();
            if (value !== member.nickname && checkedNickname !== value) {
              nicknameForm.setFields([{ name: 'nickname', errors: ['중복 확인을 해 주세요.'] }]);
              return;
            }
            nicknameMutation.mutate(value, {
              // 헤더에 표시되는 닉네임을 바꾼다.
              onSuccess: () =>
                useAuthStore.setState((state) =>
                  state.member ? { member: { ...state.member, nickname: value } } : state,
                ),
              onError: (error) => {
                if (getErrorCode(error) === 'M002') {
                  setCheckedNickname(undefined);
                  nicknameForm.setFields([{ name: 'nickname', errors: ['이미 사용 중입니다.'] }]);
                } else {
                  void message.error(getErrorMessage(error));
                }
              },
            });
          }}
        >
          <Form.Item label="닉네임" name="nickname" rules={[{ required: true, max: 30 }]}>
            <Input />
          </Form.Item>
          <Space>
            <Button onClick={() => void checkNickname()} loading={checking}>
              중복 확인
            </Button>
            <Button type="primary" htmlType="submit" loading={nicknameMutation.isPending}>
              변경
            </Button>
            {checkedNickname && <Typography.Text type="success">사용 가능</Typography.Text>}
          </Space>
        </Form>
      </Card>
      <Card title="좋아하는 맛">
        <Checkbox.Group
          options={tasteOptions}
          value={tastes}
          onChange={(values) => setTastes(values as MyInfoResponse['tastes'])}
        />
        <div className={styles.cardAction}>
          <Button
            type="primary"
            onClick={() => tastesMutation.mutate(tastes)}
            loading={tastesMutation.isPending}
          >
            저장
          </Button>
        </div>
      </Card>
      <Card title="비밀번호 변경">
        <Form
          form={passwordForm}
          layout="vertical"
          onFinish={({ currentPassword, newPassword }) =>
            passwordMutation.mutate(
              { currentPassword, newPassword },
              {
                onSuccess: () => {
                  passwordForm.resetFields();
                  void message.success('비밀번호를 변경했습니다. 다른 기기의 세션은 해제됩니다.');
                },
                onError: (error) => {
                  const code = getErrorCode(error);
                  if (code === 'M003') {
                    passwordForm.setFields([
                      { name: 'currentPassword', errors: ['현재 비밀번호가 일치하지 않습니다.'] },
                    ]);
                  } else if (code === 'M004') {
                    passwordForm.setFields([
                      {
                        name: 'newPassword',
                        errors: ['현재 비밀번호와 다른 비밀번호를 입력해 주세요.'],
                      },
                    ]);
                  } else {
                    void message.error(getErrorMessage(error));
                  }
                },
              },
            )
          }
        >
          <Form.Item label="현재 비밀번호" name="currentPassword" rules={[{ required: true }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            label="새 비밀번호"
            name="newPassword"
            rules={[
              { required: true, min: 8, max: 30 },
              {
                pattern: /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9])[\x21-\x7E]+$/,
                message: '영문, 숫자, 특수문자를 포함해 주세요.',
              },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="새 비밀번호 확인"
            name="confirm"
            dependencies={['newPassword']}
            rules={[
              { required: true },
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  value === getFieldValue('newPassword')
                    ? Promise.resolve()
                    : Promise.reject(new Error('비밀번호가 일치하지 않습니다.')),
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={passwordMutation.isPending}>
            비밀번호 변경
          </Button>
        </Form>
      </Card>
    </div>
  );
}

export default function AccountPage() {
  const { data, isPending, isError, refetch } = useQuery(memberQueries.me());

  if (isPending) return <Spin fullscreen tip="계정 정보를 불러오고 있습니다" />;
  if (isError) {
    return (
      <Result
        status="error"
        title="계정 정보를 불러오지 못했습니다"
        extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
      />
    );
  }
  return (
    <section className={styles.container}>
      <Typography.Title level={2}>내 계정</Typography.Title>
      <AccountForms member={data} />
    </section>
  );
}
