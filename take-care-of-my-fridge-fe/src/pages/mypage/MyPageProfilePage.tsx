import AccountPage from '@/pages/member/AccountPage';

// 마이페이지 안에서는 화면 제목만 "내 정보 수정"(24px)으로 바꿔 보인다.
export default function MyPageProfilePage() {
  return <AccountPage inMyPage />;
}
