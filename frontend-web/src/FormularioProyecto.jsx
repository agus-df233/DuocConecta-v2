// Ventana para publicar o editar un proyecto: el mismo formulario en los dos casos.
// Valida antes de enviar, para no depender de un 400 del servidor.
import { useState } from 'react'
import AdjuntosProyecto from './AdjuntosProyecto'
import { ESTADOS, HERRAMIENTAS_SUGERIDAS, SEDES } from './catalogo'

/** Un proyecto nuevo empieza público y buscando equipo, que es el caso más común. */
const VACIO = {
  nombre: '', resumen: '', descripcion: '', urlRepositorio: '', sede: SEDES[0],
  herramientas: [], estado: 'BUSCANDO_EQUIPO', visibilidad: 'PUBLICO', adjuntos: [],
}

export default function FormularioProyecto({ proyecto, alGuardar, alCerrar }) {
  const [datos, setDatos] = useState({ ...VACIO, ...(proyecto ?? {}) })
  const [errores, setErrores] = useState({})
  const [textos, setTextos] = useState({})   // texto crudo de los campos de lista
  const [guardando, setGuardando] = useState(false)

  const editando = Boolean(proyecto?.id)

  const campo = (clave) => ({
    value: datos[clave] ?? '',
    onChange: (e) => {
      setDatos({ ...datos, [clave]: e.target.value })
      setErrores({ ...errores, [clave]: '' })
    },
    className: errores[clave] ? 'malo' : '',
  })

  /**
   * Las listas se escriben separadas por comas, y el texto se conserva tal cual mientras se
   * escribe. Partirlo en cada tecla borraba la coma recién escrita —el elemento vacío se
   * descartaba y el campo se volvía a serializar—, así que no se podía empezar la segunda.
   * La conversión a lista ocurre al salir del campo y al enviar.
   */
  const aLista = (texto) => texto.split(',').map((x) => x.trim()).filter(Boolean)

  const lista = (clave) => ({
    value: textos[clave] ?? (datos[clave] ?? []).join(', '),
    onChange: (e) => setTextos({ ...textos, [clave]: e.target.value }),
    onBlur: () => {
      if (textos[clave] === undefined) return
      setDatos({ ...datos, [clave]: aLista(textos[clave]) })
      setTextos({ ...textos, [clave]: undefined })
    },
  })

  const enviar = async (evento) => {
    evento.preventDefault()

    // Si se envía sin salir del campo, el texto en edición todavía no pasó a lista.
    const pendientes = Object.fromEntries(
      Object.entries(textos)
        .filter(([, texto]) => texto !== undefined)
        .map(([clave, texto]) => [clave, aLista(texto)]))
    const datosFinales = { ...datos, ...pendientes }

    const fallos = {}
    if (!datos.nombre.trim()) fallos.nombre = 'Ponle un nombre al proyecto.'
    if (!datos.resumen.trim()) fallos.resumen = 'El resumen es lo único que se lee en la tarjeta.'
    if (datos.resumen.length > 200) fallos.resumen = 'El resumen es de una línea: hasta 200 caracteres.'
    if (Object.keys(fallos).length > 0) { setErrores(fallos); return }

    setGuardando(true)
    try {
      await alGuardar(datosFinales)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <>
      <div className="modal-fondo" onClick={alCerrar} />
      <div className="modal-caja">
        <div className="modal">
          <div className="modal-cabecera">
            <h2>{editando ? 'Editar proyecto' : 'Publicar un proyecto'}</h2>
            <button className="cerrar" onClick={alCerrar} aria-label="Cerrar">×</button>
          </div>

          <form onSubmit={enviar}>
            <label>
              Nombre *
              <input maxLength={150} placeholder="Nombre del proyecto" {...campo('nombre')} />
              {errores.nombre && <span className="mensaje-campo">{errores.nombre}</span>}
            </label>

            <label>
              Resumen *
              <input maxLength={200} placeholder="Una línea que resuma el proyecto" {...campo('resumen')} />
              {errores.resumen && <span className="mensaje-campo">{errores.resumen}</span>}
            </label>

            <label>
              Detalle
              <textarea
                rows={4}
                maxLength={2000}
                placeholder="Qué resuelve, en qué etapa está, qué ayuda necesitas"
                {...campo('descripcion')}
              />
            </label>

            <div className="dos-columnas">
              <label>
                Herramientas (separadas por comas)
                <input
                  list="herramientas-sugeridas"
                  placeholder={HERRAMIENTAS_SUGERIDAS.slice(0, 3).join(', ')}
                  {...lista('herramientas')}
                />
                <datalist id="herramientas-sugeridas">
                  {HERRAMIENTAS_SUGERIDAS.map((h) => <option key={h} value={h} />)}
                </datalist>
              </label>

              <label>
                Estado *
                <select {...campo('estado')}>
                  {Object.entries(ESTADOS).map(([valor, texto]) => (
                    <option key={valor} value={valor}>{texto}</option>
                  ))}
                </select>
              </label>
            </div>

            <div className="dos-columnas">
              <label>
                Sede
                <select {...campo('sede')}>
                  {SEDES.map((s) => <option key={s} value={s}>{s}</option>)}
                </select>
              </label>

              <label>
                Visibilidad
                <select {...campo('visibilidad')}>
                  <option value="PUBLICO">Visible en la vitrina</option>
                  <option value="PRIVADO">Solo para mí</option>
                </select>
              </label>
            </div>

            <label>
              Enlace al repositorio o al trabajo
              <input type="url" placeholder="https://…" {...campo('urlRepositorio')} />
            </label>

            <AdjuntosProyecto
              adjuntos={datos.adjuntos ?? []}
              alCambiar={(adjuntos) => setDatos({ ...datos, adjuntos })}
            />

            <div className="acciones">
              <button className="principal" type="submit" disabled={guardando}>
                {guardando ? 'Guardando…' : (editando ? 'Guardar cambios' : 'Publicar')}
              </button>
              <button className="secundario" type="button" onClick={alCerrar}>Cancelar</button>
            </div>
          </form>
        </div>
      </div>
    </>
  )
}
