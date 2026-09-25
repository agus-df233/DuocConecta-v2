// Ventana para pedir contacto a quien publicó un proyecto.
//
// Reemplaza al prompt() del navegador, que no se puede estilar, corta los textos largos y en
// Safari aparece pegado al borde superior sin relación con la aplicación.
import { useState } from 'react'
import { iniciales } from './catalogo'

const SUGERENCIA = 'Hola, me interesa tu proyecto y me gustaría sumarme.'

export default function PedirContacto({ proyecto, alEnviar, alCerrar }) {
  const [mensaje, setMensaje] = useState(SUGERENCIA)
  const [enviando, setEnviando] = useState(false)
  const [compartirTelefono, setCompartirTelefono] = useState(false)

  const autor = proyecto.autor ?? {}
  const nombre = autor.nombre ?? 'quien publicó este proyecto'

  const enviar = async (evento) => {
    evento.preventDefault()
    setEnviando(true)
    try {
      await alEnviar(mensaje.trim(), compartirTelefono)
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
            <h2>Pedir contacto</h2>
            <button className="cerrar" onClick={alCerrar} aria-label="Cerrar">×</button>
          </div>

          <form onSubmit={enviar}>
            <div className="destinatario">
              <div className="iniciales">{iniciales(autor.nombre)}</div>
              <div>
                <strong>{nombre}</strong>
                <span>{[autor.carrera, proyecto.sede ?? autor.sede].filter(Boolean).join(' · ')}</span>
              </div>
            </div>

            <p className="ayuda">
              Sobre su proyecto <strong>{proyecto.nombre}</strong>.
            </p>

            <label>
              Tu mensaje
              <textarea
                rows={3}
                maxLength={500}
                value={mensaje}
                onChange={(e) => setMensaje(e.target.value)}
                placeholder="Cuéntale por qué quieres escribirle"
                autoFocus
              />
            </label>

            {/* El mismo bloque que se ve en el panel de detalle: la persona tiene que saber
                que enviar no comparte nada todavía. */}
            <div className="consentimiento">
              <h4>Nada se comparte todavía</h4>
              <p>
                Pedir contacto es ofrecer el tuyo. Si {nombre.split(' ')[0]} acepta, cada uno verá
                el correo y las redes del otro. Si no acepta, no ve nada tuyo.
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
                {enviando ? 'Enviando…' : 'Enviar solicitud'}
              </button>
              <button className="secundario" type="button" onClick={alCerrar}>Cancelar</button>
            </div>
          </form>
        </div>
      </div>
    </>
  )
}
