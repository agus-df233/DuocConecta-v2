// Panel lateral con el detalle de un proyecto.
//
// La separación importa: comentar es público entre quienes ya ven el proyecto; pedir contacto es
// lo único que intercambia datos privados, y solo si la otra persona acepta.
import { useEffect, useState } from 'react'
import { listarComentarios, comentarProyecto } from './api'
import { ESTADOS, iniciales, fechaCorta, fechaLarga, TEXTO_SOLICITUD } from './catalogo'

/** Tamaño legible: 1.4 MB dice más que 1468006. */
function pesoLegible(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export default function DetalleProyecto({ proyecto, esMio, estadoSolicitud, alPedirContacto, alEditar, alBorrar, alCerrar }) {
  const [comentarios, setComentarios] = useState(null)
  const [texto, setTexto] = useState('')
  const [error, setError] = useState(null)

  const autor = proyecto.autor ?? {}
  const primerNombre = autor.nombre?.split(' ')[0] ?? 'quien lo publicó'

  useEffect(() => {
    setComentarios(null)
    listarComentarios(proyecto.id).then(setComentarios).catch(() => setComentarios([]))
  }, [proyecto.id])

  const enviarComentario = async (evento) => {
    evento.preventDefault()
    if (!texto.trim()) return
    try {
      const nuevo = await comentarProyecto(proyecto.id, texto.trim())
      setComentarios([...(comentarios ?? []), nuevo])
      setTexto('')
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <>
      <div className="telon" onClick={alCerrar} />
      <aside className="drawer">
        <div className="drawer-cabecera">
          <div style={{ flex: 1 }}>
            <span className={`insignia ${proyecto.estado}`}>{ESTADOS[proyecto.estado] ?? proyecto.estado}</span>
            <h2>{proyecto.nombre}</h2>
            <p>
              {[autor.nombre ?? 'Alguien de la comunidad', autor.carrera,
                proyecto.sede ?? autor.sede, fechaCorta(proyecto.fechaCreacion)]
                .filter(Boolean).join(' · ')}
            </p>
          </div>
          <button className="cerrar" onClick={alCerrar} aria-label="Cerrar">×</button>
        </div>

        <div className="drawer-cuerpo">
          {error && <div className="error">{error}</div>}

          <p>{proyecto.descripcion || proyecto.resumen}</p>

          {proyecto.herramientas?.length > 0 && (
            <div className="chips">
              {proyecto.herramientas.map((h) => <span key={h} className="chip">{h}</span>)}
            </div>
          )}

          {proyecto.urlRepositorio && (
            <p><a href={proyecto.urlRepositorio} target="_blank" rel="noreferrer">Ver el repositorio</a></p>
          )}

          <div className="tarjeta">
            <p className="rotulo">Archivos adjuntos</p>
            {proyecto.adjuntos?.length > 0 ? (
              proyecto.adjuntos.map((a) => (
                <div key={a.id ?? a.url} className="adjunto">
                  <span className="hoja" />
                  <a href={a.url} target="_blank" rel="noreferrer" style={{ flex: 1 }}>{a.nombre}</a>
                  {a.tamanoBytes ? <span className="ayuda">{pesoLegible(a.tamanoBytes)}</span> : null}
                </div>
              ))
            ) : (
              <p className="ayuda" style={{ margin: 0 }}>Sin archivos adjuntos.</p>
            )}
          </div>

          {/* Lo propio se administra; lo ajeno se contacta. */}
          {esMio ? (
            <div className="consentimiento">
              <h4>Este proyecto es tuyo</h4>
              <p>
                Ocultarlo lo saca de la vitrina pública sin borrar nada: tú lo sigues viendo y
                puedes volver a publicarlo cuando quieras.
              </p>
              <div className="acciones">
                <button className="secundario" onClick={alEditar}>Editar</button>
                <button className="secundario peligro" onClick={alBorrar}>Eliminar</button>
              </div>
            </div>
          ) : (
            <div className="consentimiento">
              <h4>Contacto bajo consentimiento</h4>
              <p>
                {estadoSolicitud === 'ACEPTADA'
                  ? `${primerNombre} aceptó: los datos de contacto de ambos están en tu perfil.`
                  : `Pedir contacto es ofrecer el tuyo. ${primerNombre} decide si acepta, y solo
                     entonces cada uno ve los datos del otro.`}
              </p>
              <button
                className="principal"
                disabled={Boolean(estadoSolicitud)}
                onClick={alPedirContacto}
              >
                {TEXTO_SOLICITUD[estadoSolicitud] ?? 'Pedir contacto'}
              </button>
            </div>
          )}

          <div>
            <p className="rotulo" style={{ marginBottom: 12 }}>
              Comentarios ({comentarios?.length ?? proyecto.cantidadComentarios})
            </p>

            {comentarios === null ? (
              <p className="ayuda">Cargando los comentarios…</p>
            ) : comentarios.length === 0 ? (
              <p className="ayuda">Aún nadie ha comentado. Un comentario útil vale más que un “me gusta”.</p>
            ) : (
              <div className="lista">
                {comentarios.map((c) => (
                  <div key={c.id} className="comentario">
                    <div className="quien">
                      <strong>{c.autor?.nombre ?? 'Alguien de la comunidad'}</strong>
                      <span>{fechaLarga(c.fecha)}</span>
                    </div>
                    <p>{c.texto}</p>
                  </div>
                ))}
              </div>
            )}

            <form className="escribir" onSubmit={enviarComentario} style={{ marginTop: 14 }}>
              <input
                placeholder="Escribe un comentario constructivo"
                value={texto}
                onChange={(e) => setTexto(e.target.value)}
                maxLength={1000}
              />
              <button type="submit">Comentar</button>
            </form>
          </div>
        </div>
      </aside>
    </>
  )
}
