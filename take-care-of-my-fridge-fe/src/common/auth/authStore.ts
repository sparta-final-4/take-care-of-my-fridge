import { create } from 'zustand';

import type { MyInfoResponse } from '@/domains/member/api';

type Status = 'initializing' | 'authenticated' | 'anonymous' | 'error';

interface AuthState {
  status: Status;
  member: MyInfoResponse | null;
}

export const useAuthStore = create<AuthState>(() => ({ status: 'initializing', member: null }));
