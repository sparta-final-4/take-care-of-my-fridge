import path from 'node:path';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [react()],
    resolve: {
      // tsconfig.app.json의 paths와 같게 유지
      alias: {
        '@': path.resolve(import.meta.dirname, './src'),
      },
    },
    build: {
      // 글꼴 조각(작은 woff·woff2)은 CSS에 base64로 넣지 않고 따로 둔다. 넣으면 첫 화면 CSS가 커지고
      // 화면에 없는 글자 조각까지 미리 받는다. 따로 두면 브라우저가 unicode-range로 필요한 조각만 받는다.
      assetsInlineLimit: (filePath: string) => (/\.woff2?$/.test(filePath) ? false : undefined),
    },
    server: {
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'node',
      include: ['src/**/*.test.{ts,tsx}'],
    },
  };
});
