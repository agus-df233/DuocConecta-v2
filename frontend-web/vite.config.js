// Configuración del servidor de desarrollo.
// El puerto 5173 está fijado a propósito: es el que quedó registrado como URI de redirección
// en Azure AD y el que el BFF acepta en su lista de orígenes CORS. Si cambia, hay que
// actualizarlo en los dos lados o el login deja de funcionar.
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
})
