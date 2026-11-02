import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { ROUTES } from '@/app/paths';

import { ChatEndedNotice } from './ChatEndedNotice';

describe('ChatEndedNotice', () => {
  it('지난 상담을 볼 곳을 알려 주고, 누르면 마이페이지 AI 상담 내역으로 간다', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ChatEndedNotice />
      </MemoryRouter>,
    );

    expect(html).toContain('지난 상담은 마이페이지 &gt; AI 상담 내역에서 볼 수 있어요');
    expect(html).toContain(`href="${ROUTES.mypageChats}"`);
  });
});
