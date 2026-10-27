import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// /api 요청을 Spring Boot(8080)로 넘긴다.
// 같은 출처로 보이기 때문에 세션 쿠키와 CSRF 쿠키를 CORS 설정 없이 쓸 수 있다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
