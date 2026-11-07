import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import dayjs from 'dayjs';
import 'dayjs/locale/ko';
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css';
// 제목용 글꼴(메인 띠 제목). 한글·라틴 글자 범위별로 필요한 파일만 받는다.
import '@fontsource/black-han-sans/400.css';

import { App } from '@/app/App';
import '@/styles/global.scss';

dayjs.locale('ko');

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
