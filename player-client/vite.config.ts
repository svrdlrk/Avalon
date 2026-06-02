import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), 'VITE_')
    const target = env.VITE_AVALON_SERVER_URL?.trim() || 'http://localhost:8080'

    return {
        plugins: [react()],
        server: {
            host: '0.0.0.0',
            port: 5173,
            strictPort: true,
            proxy: {
                '/ws': {
                    target,
                    changeOrigin: true,
                    ws: true,
                },
                '/sockjs': {
                    target,
                    changeOrigin: true,
                    ws: true,
                },
                '/app': {
                    target,
                    changeOrigin: true,
                    ws: true,
                },
                '/topic': {
                    target,
                    changeOrigin: true,
                    ws: true,
                }
            }
        }
    }
})