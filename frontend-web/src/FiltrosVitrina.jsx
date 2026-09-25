// Barra de filtros de la vitrina.
//
// Los desplegables salen del catálogo y no de los proyectos que existan, así están siempre
// disponibles aunque la vitrina esté vacía. Cada uno arranca en su opción sin filtro.
import { ESCUELAS, ESTADOS, HERRAMIENTAS_SUGERIDAS } from './catalogo'

export default function FiltrosVitrina({ filtros, alCambiar, proyectos, coinciden }) {
  const cambiar = (campo) => (evento) => alCambiar({ ...filtros, [campo]: evento.target.value })

  const limpiar = () => alCambiar({ texto: '', escuela: '', carrera: '', herramienta: '', estado: '' })
  const hayFiltros = Object.values(filtros).some(Boolean)

  // Las sugeridas más las que la gente haya escrito de verdad, sin repetir: el campo es libre,
  // así que alguien puede publicar con una herramienta que no está en la lista.
  const herramientas = [...new Set([
    ...HERRAMIENTAS_SUGERIDAS,
    ...valoresDe(proyectos, (p) => p.herramientas),
  ])].sort((a, b) => a.localeCompare(b, 'es'))

  /** Escuela y carrera en un solo control: separados serían el doble de ancho. */
  const elegirEscuelaOCarrera = (evento) => {
    const valor = evento.target.value
    const esEscuela = ESCUELAS.some((esc) => esc.nombre === valor)
    alCambiar({ ...filtros, escuela: esEscuela ? valor : '', carrera: esEscuela ? '' : valor })
  }

  return (
    <section className="filtros">
      <input
        className="buscador"
        placeholder="Buscar por nombre o descripción"
        value={filtros.texto}
        onChange={cambiar('texto')}
      />

      <select
        className="ancho-medio"
        value={filtros.escuela || filtros.carrera}
        onChange={elegirEscuelaOCarrera}
      >
        <option value="">Toda escuela</option>
        {ESCUELAS.map((esc) => (
          <optgroup key={esc.nombre} label={esc.nombre}>
            <option value={esc.nombre}>Toda la escuela</option>
            {esc.carreras.map((c) => <option key={c} value={c}>{c}</option>)}
          </optgroup>
        ))}
      </select>

      <select className="ancho-chico" value={filtros.herramienta} onChange={cambiar('herramienta')}>
        <option value="">Toda herramienta</option>
        {herramientas.map((h) => <option key={h} value={h}>{h}</option>)}
      </select>

      <select className="ancho-minimo" value={filtros.estado} onChange={cambiar('estado')}>
        <option value="">Todo estado</option>
        {Object.entries(ESTADOS).map(([valor, texto]) => (
          <option key={valor} value={valor}>{texto}</option>
        ))}
      </select>

      <p className="resumen"><strong>{coinciden}</strong> coinciden</p>
      {hayFiltros && <button className="enlace" onClick={limpiar}>Limpiar</button>}
    </section>
  )
}

/** Valores distintos en los proyectos. `leer` puede devolver un texto o una lista. */
function valoresDe(proyectos, leer) {
  const encontrados = new Set()
  for (const proyecto of proyectos) {
    const valor = leer(proyecto)
    for (const uno of Array.isArray(valor) ? valor : [valor]) {
      if (uno) encontrados.add(uno)
    }
  }
  return [...encontrados]
}
