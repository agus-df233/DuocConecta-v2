// Selector de archivos adjuntos de un proyecto.
//
// El archivo se sube a S3 en cuanto se elige, y lo que viaja con el proyecto es solo la
// referencia. Así el archivo no pasa por el servidor y el formulario no queda esperando.
import { useRef, useState } from 'react'
import { subirAdjunto } from './api'

const MAXIMO = 5
const TAMANO_MAXIMO = 10 * 1024 * 1024

/** Tamaño legible: 1.4 MB dice más que 1468006. */
function pesoLegible(bytes) {
  if (!bytes && bytes !== 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export default function AdjuntosProyecto({ adjuntos, alCambiar }) {
  const [subiendo, setSubiendo] = useState([])   // [{ nombre, progreso }]
  const [error, setError] = useState(null)
  const entrada = useRef(null)

  const elegir = async (evento) => {
    const archivos = Array.from(evento.target.files ?? [])
    evento.target.value = ''            // permite volver a elegir el mismo archivo
    if (archivos.length === 0) return
    setError(null)

    if (adjuntos.length + archivos.length > MAXIMO) {
      setError(`Un proyecto admite hasta ${MAXIMO} adjuntos.`)
      return
    }
    const grande = archivos.find((a) => a.size > TAMANO_MAXIMO)
    if (grande) {
      setError(`"${grande.name}" pesa más de 10 MB.`)
      return
    }

    for (const archivo of archivos) {
      setSubiendo((s) => [...s, { nombre: archivo.name, progreso: 0 }])
      try {
        const subido = await subirAdjunto(archivo, (progreso) =>
          setSubiendo((s) => s.map((x) => (x.nombre === archivo.name ? { ...x, progreso } : x))))
        alCambiar([...adjuntos, subido])
      } catch (e) {
        setError(`No se pudo subir "${archivo.name}": ${e.message}`)
      } finally {
        setSubiendo((s) => s.filter((x) => x.nombre !== archivo.name))
      }
    }
  }

  const quitar = (indice) => alCambiar(adjuntos.filter((_, i) => i !== indice))

  return (
    <div className="adjuntos-campo">
      <div className="etiqueta-campo">
        Archivos adjuntos
        <span className="ayuda"> · hasta {MAXIMO}, de 10 MB cada uno</span>
      </div>

      {adjuntos.map((a, i) => (
        <div key={a.claveS3 ?? a.urlExterna ?? i} className="adjunto">
          <span className="hoja" />
          <span style={{ flex: 1 }}>{a.nombre}</span>
          <span className="ayuda">{pesoLegible(a.tamanoBytes)}</span>
          <button type="button" className="cerrar" onClick={() => quitar(i)} aria-label="Quitar">×</button>
        </div>
      ))}

      {subiendo.map((s) => (
        <div key={s.nombre} className="adjunto">
          <span className="hoja" />
          <span style={{ flex: 1 }}>{s.nombre}</span>
          <span className="ayuda">{s.progreso}%</span>
        </div>
      ))}

      {error && <p className="mensaje-campo">{error}</p>}

      <input
        ref={entrada}
        type="file"
        multiple
        style={{ display: 'none' }}
        onChange={elegir}
      />
      <button
        type="button"
        className="secundario"
        onClick={() => entrada.current?.click()}
        disabled={adjuntos.length >= MAXIMO || subiendo.length > 0}
      >
        {subiendo.length > 0 ? 'Subiendo…' : 'Elegir archivos'}
      </button>
    </div>
  )
}
