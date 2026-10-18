// Filtros de la vitrina: un buscador, grupos de chips y el orden. Un chip se ve y se toca de una
// vez; un desplegable esconde las opciones detrás de un clic.
//
// Escuela abre una segunda fila con sus carreras. Tecnología muestra las herramientas más usadas en
// los proyectos que existen, no una lista fija: el campo es libre y cada carrera usa las suyas.
// Cada chip dice cuántos proyectos quedarían al elegirlo, contando los demás filtros activos.
import { ESCUELAS, ESTADOS, escuelaDe, nombreCorto } from './catalogo'

export const SIN_FILTROS = { texto: '', escuela: '', carrera: '', sede: '', herramienta: '', estado: '' }
export const ORDENES = { recientes: 'Más recientes', comentados: 'Más comentados' }
const MAS_USADAS = 10

export default function FiltrosVitrina({ filtros, alCambiar, proyectos, coinciden, orden, alOrdenar }) {
  const poner = (cambios) => alCambiar({ ...filtros, ...cambios })
  // Tocar el chip activo lo apaga: así no hace falta un "todos" en cada fila.
  const alternar = (campo, valor) => poner({ [campo]: filtros[campo] === valor ? '' : valor })
  const activos = filtrosActivos(filtros)

  // Cuántos proyectos quedarían si este chip estuviera elegido, con el resto de los filtros igual.
  const cuantos = (cambios) => proyectos.filter((p) => cumple(p, { ...filtros, ...cambios })).length

  const escuela = ESCUELAS.find((e) => e.nombre === filtros.escuela)
  const herramientas = masUsadas(proyectos, filtros.herramienta)
  // Hoy la plataforma tiene una sola sede: el grupo aparece solo cuando hay de dónde elegir.
  const sedes = sedesDe(proyectos, filtros.sede)

  return (
    <section className="filtros" aria-label="Filtros de la vitrina">
      <label className="buscador">
        <span className="rotulo">Buscar</span>
        <input
          type="search"
          placeholder="Proyecto, herramienta o autor"
          value={filtros.texto}
          onChange={(e) => poner({ texto: e.target.value })}
        />
      </label>

      <GrupoChips etiqueta="Escuela">
        {ESCUELAS.map((e) => (
          <Chip key={e.nombre} activo={filtros.escuela === e.nombre}
                cantidad={cuantos({ escuela: e.nombre, carrera: '' })}
                alTocar={() => poner({ escuela: filtros.escuela === e.nombre ? '' : e.nombre, carrera: '' })}>
            {nombreCorto(e.nombre)}
          </Chip>
        ))}
      </GrupoChips>

      {escuela && (
        <GrupoChips etiqueta="Carrera">
          {escuela.carreras.map((c) => (
            <Chip key={c} activo={filtros.carrera === c} cantidad={cuantos({ carrera: c })}
                  alTocar={() => alternar('carrera', c)}>{c}</Chip>
          ))}
        </GrupoChips>
      )}

      {sedes.length > 1 && (
        <GrupoChips etiqueta="Sede">
          {sedes.map((s) => (
            <Chip key={s} activo={filtros.sede === s} cantidad={cuantos({ sede: s })}
                  alTocar={() => alternar('sede', s)}>{s}</Chip>
          ))}
        </GrupoChips>
      )}

      {herramientas.length > 0 && (
        <GrupoChips etiqueta="Tecnología">
          {herramientas.map((h) => (
            <Chip key={h} activo={filtros.herramienta === h} cantidad={cuantos({ herramienta: h })}
                  alTocar={() => alternar('herramienta', h)}>{h}</Chip>
          ))}
        </GrupoChips>
      )}

      <GrupoChips etiqueta="Estado">
        {Object.entries(ESTADOS).map(([valor, texto]) => (
          <Chip key={valor} activo={filtros.estado === valor} cantidad={cuantos({ estado: valor })}
                alTocar={() => alternar('estado', valor)}>{texto}</Chip>
        ))}
      </GrupoChips>

      <div className="filtros-pie">
        <p role="status"><strong>{coinciden}</strong> {coinciden === 1 ? 'proyecto coincide' : 'proyectos coinciden'} con tus filtros</p>
        {activos.length > 0 && (
          <ul className="filtros-activos" aria-label="Filtros activos">
            {activos.map((a) => (
              <li key={a.campo}>
                <button type="button" className="filtro-activo" onClick={() => poner(a.limpiar)}
                        aria-label={`Quitar el filtro ${a.texto}`}>
                  {a.texto}<span aria-hidden="true">×</span>
                </button>
              </li>
            ))}
            <li>
              <button type="button" className="enlace" onClick={() => alCambiar(SIN_FILTROS)}>Limpiar filtros</button>
            </li>
          </ul>
        )}
        <label className="orden">
          <span>Ordenar por</span>
          <select value={orden} onChange={(e) => alOrdenar(e.target.value)}>
            {Object.entries(ORDENES).map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
          </select>
        </label>
      </div>
    </section>
  )
}

/** Una fila de chips con su rótulo. */
function GrupoChips({ etiqueta, children }) {
  return (
    <div className="grupo-chips" role="group" aria-label={etiqueta}>
      <span className="rotulo">{etiqueta}</span>
      <div className="chips">{children}</div>
    </div>
  )
}

/**
 * Un chip que se prende y se apaga, con la cantidad de proyectos que dejaría. Uno que dejaría cero
 * se ve atenuado pero se puede tocar igual: así se llega al estado vacío y a su "publica el primero".
 */
function Chip({ activo, cantidad, alTocar, children }) {
  const clases = ['chip-filtro', activo && 'activo', !activo && cantidad === 0 && 'sin-resultados'].filter(Boolean)
  return (
    <button type="button" aria-pressed={activo} className={clases.join(' ')} onClick={alTocar}>
      {children}<span className="cantidad">{cantidad}</span>
    </button>
  )
}

/** Sin tildes ni mayúsculas: "diseno" encuentra "Diseño". */
const normalizar = (texto) => (texto ?? '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

/** La sede del proyecto, o la de quien lo publicó si el proyecto no la trae. */
const sedeDe = (p) => p.sede ?? p.autor?.sede ?? ''

/**
 * Si un proyecto pasa todos los filtros. Escuela y carrera salen del perfil de quien publicó: el
 * proyecto no las guarda.
 */
function cumple(p, f) {
  const buscado = normalizar(f.texto.trim())
  const texto = normalizar([p.nombre, p.resumen, p.descripcion, p.autor?.nombre, ...(p.herramientas ?? [])].join(' '))
  return (!buscado || texto.includes(buscado))
    && (!f.escuela || escuelaDe(p.autor?.carrera) === f.escuela)
    && (!f.carrera || p.autor?.carrera === f.carrera)
    && (!f.sede || sedeDe(p) === f.sede)
    && (!f.herramienta || p.herramientas?.includes(f.herramienta))
    && (!f.estado || p.estado === f.estado)
}

/** Los proyectos que pasan los filtros, en el orden elegido. */
export function filtrarYOrdenar(proyectos, filtros, orden) {
  const fecha = (p) => Date.parse(p.fechaCreacion ?? '') || 0
  const porFecha = (a, b) => fecha(b) - fecha(a)
  const comparar = orden === 'comentados'
    ? (a, b) => (b.cantidadComentarios ?? 0) - (a.cantidadComentarios ?? 0) || porFecha(a, b)
    : porFecha
  return proyectos.filter((p) => cumple(p, filtros)).sort(comparar)
}

/** Cada filtro elegido, con su texto y cómo quitarlo. Escuela se lleva también la carrera. */
function filtrosActivos(f) {
  const lista = []
  if (f.texto.trim()) lista.push({ campo: 'texto', texto: `“${f.texto.trim()}”`, limpiar: { texto: '' } })
  if (f.escuela) lista.push({ campo: 'escuela', texto: nombreCorto(f.escuela), limpiar: { escuela: '', carrera: '' } })
  if (f.carrera) lista.push({ campo: 'carrera', texto: f.carrera, limpiar: { carrera: '' } })
  if (f.sede) lista.push({ campo: 'sede', texto: f.sede, limpiar: { sede: '' } })
  if (f.herramienta) lista.push({ campo: 'herramienta', texto: f.herramienta, limpiar: { herramienta: '' } })
  if (f.estado) lista.push({ campo: 'estado', texto: ESTADOS[f.estado] ?? f.estado, limpiar: { estado: '' } })
  return lista
}

/** Las sedes que aparecen en la vitrina, en orden alfabético. La elegida se mantiene aunque ya no esté. */
function sedesDe(proyectos, elegida) {
  const sedes = new Set(proyectos.map(sedeDe).filter(Boolean))
  if (elegida) sedes.add(elegida)
  return [...sedes].sort((a, b) => a.localeCompare(b, 'es'))
}

/** Las herramientas más repetidas en la vitrina. La elegida se mantiene aunque ya no esté entre ellas. */
function masUsadas(proyectos, elegida) {
  const veces = new Map()
  for (const p of proyectos) {
    for (const h of p.herramientas ?? []) veces.set(h, (veces.get(h) ?? 0) + 1)
  }
  const top = [...veces.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'es'))
    .slice(0, MAS_USADAS)
    .map(([h]) => h)
  return elegida && !top.includes(elegida) ? [...top, elegida] : top
}
