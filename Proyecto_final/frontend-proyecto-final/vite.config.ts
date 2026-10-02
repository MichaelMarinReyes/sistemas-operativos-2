import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxy = {
    '/api': {
      target: env.API_PROXY_TARGET || 'http://localhost:18080',
      changeOrigin: true,
    },
  }

  return {
    plugins: [vue()],
    server: { port: Number(env.DEV_SERVER_PORT || 15173), strictPort: true, proxy },
    preview: { proxy },
  }
})
