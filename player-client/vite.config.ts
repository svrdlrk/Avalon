import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), 'VITE_')
    // Do not use "localhost" here. On Windows Node may resolve it to ::1 while
    // Spring Boot is intentionally bound to IPv4 (0.0.0.0) for LAN clients.
    // That made the Vite page available on :5173 but broke every proxied route:
    // /ws, /api and /uploads.
    const configuredTarget = env.VITE_AVALON_SERVER_URL?.trim()
    const target = (configuredTarget || 'http://127.0.0.1:8080')
        .replace(/^http:\/\/localhost(?=[:/]|$)/i, 'http://127.0.0.1')

    return {
        plugins: [react()],
        server: {
            host: '0.0.0.0',
            port: 5173,
            strictPort: true,
            proxy: {
                // WebSocket / STOMP
                '/ws': {
                    target,
                    changeOrigin: true,
                    ws: true,
                },
                '/ws-native': {
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
                },
                // Static assets served by Spring Boot.
                // Without this proxy, mobile devices (same WiFi, port 8080 blocked by
                // Windows Firewall) cannot load map backgrounds or token images.
                '/uploads': {
                    target,
                    changeOrigin: true,
                },
                // REST API (session create, save, server-info, asset catalog …)
                '/api': {
                    target,
                    changeOrigin: true,
                },
            },
        },
    }
})
