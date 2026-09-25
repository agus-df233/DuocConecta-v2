// Punto de arranque del SPA.
// MSAL tiene que inicializarse antes de pintar nada: si React monta primero, cualquier
// componente que pida el token se encuentra con un cliente a medio configurar.
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
