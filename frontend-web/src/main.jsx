// Punto de arranque del SPA.
// La sesión se prepara antes de pintar nada: si React monta primero, la vuelta del login
// (de Microsoft o de Google) todavía no se procesó y la aplicación mostraría la pantalla de entrada.
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { inicializar } from './auth'
import './estilos.css'

inicializar().then(() => {
  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  )
})
