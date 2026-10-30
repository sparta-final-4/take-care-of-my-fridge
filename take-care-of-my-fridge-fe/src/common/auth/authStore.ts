import { create } from 'zustand';

import type { MyInfoResponse } from '@/common/api/clients';

type Status = 'initializing' | 'authenticated' | 'anonymous' | 'error';

interface AuthState {
  status: Status;
  member: MyInfoResponse | null;
  reason: 'expired' | 'suspended' | null;
}

export const useAuthStore = create<AuthState>(() => ({
  status: 'initializing',
  member: null,
  reason: null,
}));
