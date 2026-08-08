import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import tsconfigPaths from 'vite-tsconfig-paths';

export default defineConfig({
  plugins: [react(), tailwindcss(), tsconfigPaths()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    // 本地 RocketMQ Proxy 占用 8080 时，Java API 可切换到 18080；生产由部署层注入。
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
        // 后端会校验 Origin，代理目标变更时必须同步发送同源 Origin。
        headers: { origin: new URL(process.env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:8080').origin },
      },
    },
  },
});
