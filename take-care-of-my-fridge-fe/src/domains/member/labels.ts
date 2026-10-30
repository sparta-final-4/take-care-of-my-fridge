import type {
  MyInfoResponseRoleEnum,
  MyInfoResponseStatusEnum,
  MyInfoResponseTastesEnum,
} from '@/common/api/clients';

export const TASTE_OPTIONS: { label: string; value: MyInfoResponseTastesEnum }[] = [
  { label: '짠맛', value: 'SALTY' },
  { label: '단맛', value: 'SWEET' },
  { label: '매운맛', value: 'SPICY' },
  { label: '감칠맛', value: 'UMAMI' },
  { label: '담백한 맛', value: 'MILD' },
];

export const ROLE_LABELS: Record<MyInfoResponseRoleEnum, string> = {
  USER: '일반 회원',
  ADMIN: '관리자',
};

export const STATUS_LABELS: Record<MyInfoResponseStatusEnum, string> = {
  ACTIVE: '정상',
  SUSPENDED: '정지',
};
