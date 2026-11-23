import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const gateway = env.VITE_GATEWAY_URL || 'http://127.0.0.1:9000'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        // 兜底：若把 VITE_API_BASE_URL 配成相对路径，开发服务器转发到网关
        '/api': {
          target: gateway,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      chunkSizeWarningLimit: 1600,
    },
  }
})
