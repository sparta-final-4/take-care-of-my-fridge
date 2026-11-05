import type { FormRule } from 'antd';

// 결제 비밀번호 자릿수. 서버(PaymentPin*Request의 \d{6})와 같은 값이다
export const PIN_LENGTH = 6;

// 숫자가 아닌 글자를 지우고 6자리까지만 남긴다. 입력 칸의 normalize로 쓴다
export function onlyDigits(value: string | undefined): string {
  return (value ?? '').replace(/\D/g, '').slice(0, PIN_LENGTH);
}

export const PIN_RULES: FormRule[] = [
  { required: true, message: '결제 비밀번호 6자리를 입력해 주세요.' },
  { len: PIN_LENGTH, message: '숫자 6자리를 입력해 주세요.' },
];

// 새 비밀번호 확인 칸. newPin과 같아야 한다
export const CONFIRM_PIN_RULES: FormRule[] = [
  { required: true, message: '새 결제 비밀번호를 한 번 더 입력해 주세요.' },
  ({ getFieldValue }) => ({
    validator: (_, value) =>
      !value || value === getFieldValue('newPin')
        ? Promise.resolve()
        : Promise.reject(new Error('새 결제 비밀번호와 같게 입력해 주세요.')),
  }),
];
