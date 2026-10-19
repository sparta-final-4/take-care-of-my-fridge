// 경로 문자열은 여기에만 둔다. 파라미터 경로는 generatePath로 만든다.
export const ROUTES = {
  home: '/',
  fridge: '/fridge',
  login: '/login',
  signup: '/signup',
  signupComplete: '/signup/complete',
  account: '/mypage/account',
  adminMembers: '/admin/members',
  adminAudit: '/admin/audit',
} as const;
