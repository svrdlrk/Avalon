import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    server: {
        host: '127.0.0.1',
        port: 5173,
        strictPort: true,
        proxy: {
            '/ws': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                ws: true,
            },
            '/sockjs': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                ws: true,
            },
            '/app': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                ws: true,
            },
            '/topic': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                ws: true,
            }
        }
    }
})