// Confirmación con estilo propio. Reemplaza al confirm() del navegador, que no se puede leer bien
// en pantallas chicas, no se traduce y no deja explicar qué se pierde.
import { useState } from 'react'
import Ventana from './Ventana'

export default function Confirmar({ titulo, children, textoAccion, alConfirmar, alCerrar }) {
  const [enviando, setEnviando] = useState(false)

  const confirmar = async () => {
    setEnviando(true)
    try {
      await alConfirmar()
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Ventana titulo={titulo} alCerrar={alCerrar} enfocarCampo={false}>
      <div className="cuerpo-modal">
        <p className="texto-confirmar">{children}</p>
        <div className="acciones">
          <button type="button" className="principal peligro-lleno" onClick={confirmar} disabled={enviando}>
            {enviando ? 'Un momento…' : textoAccion}
          </button>
          <button type="button" className="secundario" onClick={alCerrar}>Cancelar</button>
        </div>
      </div>
    </Ventana>
  )
}
