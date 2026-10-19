// Panel lateral con el detalle de un proyecto.
//
// La separación importa: comentar es público entre quienes ya ven el proyecto; pedir colaborar es
// lo único que intercambia datos privados, y solo si la otra persona acepta.
import { useEffect, useId, useRef, useState } from 'react'
import { listarComentarios, comentarProyecto } from './api'
import { ESTADOS, fechaCorta, fechaRelativa, TEXTO_SOLICITUD } from './catalogo'
import { useAtraparFoco } from './Ventana'

/** Tamaño legible: 1.4 MB dice más que 1468006. */
function pesoLegible(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export default function DetalleProyecto({
  proyecto, esMio, estadoSolicitud, enfocarComentario,
  alColaborar, alCompartir, alEditar, alBorrar, alCerrar,
}) {
  const [comentarios, setComentarios] = useState(null)
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const caja = useAtraparFoco(alCerrar)
  const campo = useRef(null)
  const titulo = useId()

  const autor = proyecto.autor ?? {}
  const primerNombre = autor.nombre?.split(' ')[0] ?? 'quien lo publicó'

  useEffect(() => {
    setComentarios(null)
    listarComentarios(proyecto.id).then(setComentarios).catch(() => setComentarios([]))
  }, [proyecto.id])

  // "Comentar" desde la tarjeta abre el panel con el cursor ya en el campo.
  useEffect(() => {
    if (enfocarComentario) campo.current?.focus()
  }, [enfocarComentario])

  const enviarComentario = async (evento) => {
    evento.preventDefault()
    if (!texto.trim() || enviando) return
    setEnviando(true)
    setError(null)
    try {
      const nuevo = await comentarProyecto(proyecto.id, texto.trim())
      setComentarios([...(comentarios ?? []), { ...nuevo, recien: true }])
      setTexto('')
    } catch (e) {
      setError(e.message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <>
      <div className="telon" onClick={alCerrar} />
      <aside className="drawer" role="dialog" aria-modal="true" aria-labelledby={titulo} ref={caja} tabIndex={-1}>
        <div className="drawer-cabecera">
          <div className="crece">
            <span className={`insignia ${proyecto.estado}`}>{ESTADOS[proyecto.estado] ?? proyecto.estado}</span>
            <h2 id={titulo}>{proyecto.nombre}</h2>
            <p>
              {[autor.nombre ?? 'Alguien de la comunidad', autor.carrera,
                proyecto.sede ?? autor.sede, fechaCorta(proyecto.fechaCreacion)]
                .filter(Boolean).join(' · ')}
            </p>
          </div>
          <button type="button" className="cerrar" onClick={alCerrar} aria-label="Cerrar">×</button>
        </div>

        <div className="drawer-cuerpo">
          {error && <div className="error" role="alert">{error}</div>}

          <p>{proyecto.descripcion || proyecto.resumen}</p>

          {proyecto.herramientas?.length > 0 && (
            <div className="chips">
              {proyecto.herramientas.map((h) => <span key={h} className="chip">{h}</span>)}
            </div>
          )}

          {proyecto.urlRepositorio && (
            <p>
              <a href={proyecto.urlRepositorio} target="_blank" rel="noreferrer">
                Ver el repositorio<span className="solo-lector"> (se abre en otra pestaña)</span>
              </a>
            </p>
          )}

          <div className="tarjeta bloque">
            <p className="rotulo">Archivos adjuntos</p>
            {proyecto.adjuntos?.length > 0 ? (
              proyecto.adjuntos.map((a) => (
                <div key={a.id ?? a.url} className="adjunto">
                  <span className="hoja" aria-hidden="true" />
                  <a href={a.url} target="_blank" rel="noreferrer" className="crece">{a.nombre}</a>
                  {a.tamanoBytes ? <span className="ayuda">{pesoLegible(a.tamanoBytes)}</span> : null}
                </div>
              ))
            ) : (
              <p className="ayuda sin-margen">Sin archivos adjuntos.</p>
            )}
          </div>

          {/* Lo propio se administra; lo ajeno se contacta. Lo abierto solo se comenta. */}
          {proyecto.abierto ? (
            <div className="consentimiento">
              <h4><span className="candado" aria-hidden="true" />Proyecto abierto</h4>
              <p>
                Lo publicó DuocConecta para mostrar lo que se puede hacer en la comunidad. Puedes
                comentarlo, pero no recibe solicitudes de colaboración.
              </p>
              <button type="button" className="secundario ancho" onClick={alCompartir}>Compartir proyecto</button>
            </div>
          ) : esMio ? (
            <div className="consentimiento">
              <h4><span className="candado" aria-hidden="true" />Este proyecto es tuyo</h4>
              <p>
                Ocultarlo lo saca de la vitrina pública sin borrar nada: tú lo sigues viendo y
                puedes volver a publicarlo cuando quieras.
              </p>
              <div className="acciones">
                <button type="button" className="secundario" onClick={alEditar}>Editar</button>
                <button type="button" className="secundario" onClick={alCompartir}>Compartir</button>
                <button type="button" className="secundario peligro" onClick={alBorrar}>Eliminar</button>
              </div>
            </div>
          ) : (
            <div className="consentimiento">
              <h4><span className="candado" aria-hidden="true" />Contacto bajo consentimiento</h4>
              <p>
                {estadoSolicitud === 'ACEPTADA'
                  ? `${primerNombre} aceptó: los datos de contacto de ambos están en tu perfil.`
                  : `Al enviar la solicitud, ${primerNombre} decide si acepta. Los datos de contacto de
                     ambos se intercambian solo si acepta.`}
              </p>
              <button
                type="button"
                className="principal ancho"
                disabled={Boolean(estadoSolicitud)}
                onClick={alColaborar}
              >
                {TEXTO_SOLICITUD[estadoSolicitud] ?? 'Colaborar'}
              </button>
              <button type="button" className="secundario ancho" onClick={alCompartir}>Compartir proyecto</button>
            </div>
          )}

          <div>
            <p className="rotulo">
              Comentarios ({comentarios?.length ?? proyecto.cantidadComentarios ?? 0})
            </p>

            {comentarios === null ? (
              <p className="ayuda">Cargando los comentarios…</p>
            ) : comentarios.length === 0 ? (
              <p className="ayuda">Aún nadie ha comentado. Un comentario útil vale más que un “me gusta”.</p>
            ) : (
              <div className="lista">
                {comentarios.map((c) => (
                  <div key={c.id} className={c.recien ? 'comentario nuevo' : 'comentario'}>
                    <div className="quien">
                      <strong>{c.autor?.nombre ?? 'Alguien de la comunidad'}</strong>
                      <span>{fechaRelativa(c.fecha)}</span>
                      {c.recien && <span className="insignia-nuevo">Nuevo</span>}
                    </div>
                    <p>{c.texto}</p>
                  </div>
                ))}
              </div>
            )}

            <form className="escribir" onSubmit={enviarComentario}>
              <label className="solo-lector" htmlFor={`comentario-${proyecto.id}`}>Tu comentario</label>
              <input
                id={`comentario-${proyecto.id}`}
                ref={campo}
                placeholder="Escribe un comentario constructivo"
                value={texto}
                onChange={(e) => setTexto(e.target.value)}
                maxLength={1000}
              />
              <button type="submit" disabled={enviando || !texto.trim()}>
                {enviando ? 'Enviando…' : 'Comentar'}
              </button>
            </form>
          </div>
        </div>
      </aside>
    </>
  )
}
