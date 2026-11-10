import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server only: forward backend paths the same way Nginx does in production,
// so `npm run dev` works against the local backend (http://localhost:8080) and the
// refresh-token cookie is same-origin, exactly like in production.
const backend = process.env.VITE_DEV_BACKEND ?? 'http://localhost:8080'

export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            '/api': backend,
            '/oauth2': backend,
            '/login/oauth2': backend,
        },
    },
})
