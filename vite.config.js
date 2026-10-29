import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ mode }) => {
  const {
    VITE_API_JAVA_TARGET: javaTarget,
  } = loadEnv(mode, '.', 'VITE_');
  const proxy = {
    ...(javaTarget ? {
      '/api/auth': {
        target: javaTarget,
        changeOrigin: true,
        cookieDomainRewrite: '',
      },
      '/api/admin': {
        target: javaTarget,
        changeOrigin: true,
      },
      '/api/wineries': {
        target: javaTarget,
        changeOrigin: true,
      },
      '/api/environments': {
        target: javaTarget,
        changeOrigin: true,
      },
      '/api/devices': {
        target: javaTarget,
        changeOrigin: true,
      },
    } : {}),
  };

  return {
    plugins: [react(), tailwindcss()],
    ...(Object.keys(proxy).length > 0 ? {
      server: {
        proxy,
      },
    } : {}),
  };
});
