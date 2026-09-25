// Ventana para aceptar una solicitud de colaboración.
//
// Reemplaza al confirm() del navegador: es la decisión más importante del producto —el momento
// exacto del consentimiento— y merece verse como tal, no como un diálogo del sistema.
import { useState } from 'react'
import { iniciales } from './catalogo'

export default function AceptarSolicitud({ solicitud, alConfirmar, alCerrar }) {
  const [compartirTelefono, setCompartirTelefono] = useState(false)
  const [enviando, setEnviando] = useState(false)

  const quien = solicitud.solicitante ?? {}
  const nombre = quien.nombre ?? 'Alguien de la comunidad'

  const confirmar = async (evento) => {
    evento.preventDefault()
    setEnviando(true)
    try {
      await alConfirmar(compartirTelefono)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <>
      <div className="modal-fondo" onClick={alCerrar} />
      <div className="modal-caja">
        <div className="modal">
          <div className="modal-cabecera">
            <h2>Aceptar solicitud</h2>
            <button className="cerrar" onClick={alCerrar} aria-label="Cerrar">×</button>
          </div>

          <form onSubmit={confirmar}>
            <div className="destinatario">
              <div className="iniciales">{iniciales(quien.nombre)}</div>
              <div>
                <strong>{nombre}</strong>
                <span>{[quien.carrera, quien.sede].filter(Boolean).join(' · ')}</span>
              </div>
            </div>

            {solicitud.proyectoNombre && (
              <p className="ayuda">Sobre <strong>{solicitud.proyectoNombre}</strong>.</p>
            )}

            <div className="consentimiento">
              <h4>Qué se comparte al aceptar</h4>
              <p>
                Tu correo institucional y tus redes. A cambio verás los datos que
                {' '}{nombre.split(' ')[0]} ofreció al escribirte.
              </p>
              <label className="casilla">
                <input
                  type="checkbox"
                  checked={compartirTelefono}
                  onChange={(e) => setCompartirTelefono(e.target.checked)}
                />
                Compartir también mi teléfono
              </label>
            </div>

            <div className="acciones">
              <button className="principal" type="submit" disabled={enviando}>
                {enviando ? 'Aceptando…' : 'Aceptar y compartir'}
              </button>
              <button className="secundario" type="button" onClick={alCerrar}>Cancelar</button>
            </div>
          </form>
        </div>
      </div>
    </>
  )
}
