// Listas fijas de la plataforma, en un solo lugar.
//
// Carreras y sede salen de `sede-carreras.json`, el listado oficial de Viña del Mar. Cuando
// cambien, se reemplaza ese archivo y nada más.
import datosSede from './sede-carreras.json'

/** Escuelas con sus carreras, tal como vienen del listado oficial. */
export const ESCUELAS = datosSede.escuelas

/** Nombre corto de una escuela, para los chips: "Escuela de Diseño" → "Diseño". */
export function nombreCorto(escuela) {
  return escuela.replace(/^Escuela de /, '')
}

/** Las carreras en una sola lista, derivada de las escuelas. */
export const CARRERAS = ESCUELAS.flatMap((e) => e.carreras)

/** Para quienes no cursan una carrera: profesores y personal académico. */
export const SIN_CARRERA = 'Otra / No aplica'

// Índice carrera → escuela. Se arma una sola vez al cargar la aplicación.
const escuelaPorCarrera = new Map(
  ESCUELAS.flatMap((e) => e.carreras.map((c) => [c, e.nombre])),
)

/**
 * La escuela de una carrera. Es lo que permite filtrar la vitrina por escuela, porque el
 * proyecto guarda quién lo publicó y la carrera sale de su perfil. null si no está en el listado.
 */
export function escuelaDe(carrera) {
  return carrera ? escuelaPorCarrera.get(carrera) ?? null : null
}

/** Sedes de la plataforma. Hoy el caso de estudio es una sola. */
export const SEDES = ['Viña del Mar']

/** Estados de un proyecto. Traducir aquí evita repartir 'BUSCANDO_EQUIPO' por toda la interfaz. */
export const ESTADOS = {
  BUSCANDO_EQUIPO: 'Busca equipo',
  EN_DESARROLLO: 'En desarrollo',
  TERMINADO: 'Terminado',
}

/** Estados de una solicitud de colaboración. */
export const ESTADOS_SOLICITUD = {
  PENDIENTE: 'Pendiente',
  ACEPTADA: 'Aceptada',
  RECHAZADA: 'Rechazada',
  RETIRADA: 'Retirada',
}

/**
 * Sugerencias al publicar; el campo acepta cualquier texto. Variadas a propósito: la plataforma
 * es de toda la comunidad, así que Figma y Excel pesan tanto como React.
 */
export const HERRAMIENTAS_SUGERIDAS = [
  'React', 'Spring Boot', 'Python', 'Flutter', 'PostgreSQL',
  'Figma', 'Illustrator', 'Canva', 'Premiere', 'Blender',
  'Excel', 'Power BI', 'Word', 'AutoCAD', 'SketchUp',
]

/** Iniciales de una persona, para el avatar. Devuelve dos letras como máximo. */
export function iniciales(nombre) {
  if (!nombre) return '··'
  return nombre
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte[0].toUpperCase())
    .join('')
}

/** Fecha corta y legible. Si no viene nada, no se muestra nada. */
export function fechaCorta(valor) {
  if (!valor) return ''
  return new Date(valor).toLocaleDateString('es-CL', { day: 'numeric', month: 'short' })
}

/** Fecha con año, para las solicitudes. */
export function fechaLarga(valor) {
  if (!valor) return ''
  return new Date(valor).toLocaleDateString('es-CL', { day: 'numeric', month: 'long', year: 'numeric' })
}

/**
 * Fecha relativa para los avisos y la vitrina: "Hace 5 min" se entiende antes que una fecha.
 * Pasada una semana vuelve a la fecha corta.
 */
export function fechaRelativa(valor) {
  if (!valor) return ''
  const minutos = Math.round((Date.now() - new Date(valor).getTime()) / 60000)
  if (minutos < 1) return 'Ahora'
  if (minutos < 60) return `Hace ${minutos} min`
  const horas = Math.round(minutos / 60)
  if (horas < 24) return `Hace ${horas} h`
  const dias = Math.round(horas / 24)
  if (dias < 7) return dias === 1 ? 'Ayer' : `Hace ${dias} días`
  return fechaCorta(valor)
}

/**
 * Cómo se ve y adónde lleva cada aviso, según el tipo del evento que lo generó en
 * ms-notificaciones. `clase` da el color; `destino` dice qué abrir al hacer clic.
 */
export const TIPOS_AVISO = {
  'colaboracion.solicitada': { etiqueta: 'Solicitud', clase: 'solicitud', accion: 'Revisar solicitud', destino: 'recibidas' },
  'colaboracion.aceptada': { etiqueta: 'Colaboración', clase: 'aceptada', accion: 'Ver contacto', destino: 'colaboraciones' },
  'colaboracion.rechazada': { etiqueta: 'Solicitud', clase: 'sistema', accion: 'Ver mis solicitudes', destino: 'enviadas' },
  'colaboracion.retirada': { etiqueta: 'Solicitud', clase: 'sistema', accion: 'Ver mis solicitudes', destino: 'recibidas' },
  'proyecto.comentado': { etiqueta: 'Comentario', clase: 'comentado', accion: 'Ver comentario', destino: 'proyecto' },
  'proyecto.colaborador_agregado': { etiqueta: 'Colaboración', clase: 'aceptada', accion: 'Ver proyecto', destino: 'proyecto' },
  'proyecto.ocultado': { etiqueta: 'Moderación', clase: 'moderacion', accion: 'Ver mis proyectos', destino: 'mis-proyectos' },
  'proyecto.restaurado': { etiqueta: 'Moderación', clase: 'aceptada', accion: 'Ver proyecto', destino: 'proyecto' },
  'proyecto.comentario_eliminado': { etiqueta: 'Moderación', clase: 'moderacion', accion: 'Ver proyecto', destino: 'proyecto' },
  'proyecto.eliminado': { etiqueta: 'Moderación', clase: 'moderacion', accion: 'Ver mis proyectos', destino: 'mis-proyectos' },
}

/** El tipo de un aviso, con un valor neutro para los tipos que todavía no están en la tabla. */
export function tipoDeAviso(tipo) {
  return TIPOS_AVISO[tipo] ?? { etiqueta: 'Aviso', clase: 'sistema', accion: 'Abrir', destino: 'perfil' }
}

/** Qué decir en el botón según cómo esté la solicitud por este proyecto. */
export const TEXTO_SOLICITUD = {
  PENDIENTE: 'Solicitud enviada',
  ACEPTADA: 'Ya colaboran',
}
