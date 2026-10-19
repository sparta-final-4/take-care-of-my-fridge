import { queryOptions } from '@tanstack/react-query';

import { checkEmailAvailability, checkNicknameAvailability, getMyInfo } from './api';

export const memberQueries = {
  all: ['member'] as const,
  me: () =>
    queryOptions({
      queryKey: [...memberQueries.all, 'me'],
      queryFn: ({ signal }) => getMyInfo(signal),
    }),
  emailAvailability: (email: string) =>
    queryOptions({
      queryKey: [...memberQueries.all, 'email-availability', email],
      queryFn: ({ signal }) => checkEmailAvailability(email, signal),
    }),
  nicknameAvailability: (nickname: string) =>
    queryOptions({
      queryKey: [...memberQueries.all, 'nickname-availability', nickname],
      queryFn: ({ signal }) => checkNicknameAvailability(nickname, signal),
    }),
};
