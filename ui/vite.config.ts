import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['src/test/setup.ts'],
    // Tests fake `fetch` and `EventSource` with vi.stubGlobal; each test starts from the real globals.
    unstubGlobals: true,
    restoreMocks: true,
    // Like the backend tests (ADR 034): a zone far from UTC, with daylight saving time.
    env: { TZ: 'Australia/Sydney' },
  },
})
