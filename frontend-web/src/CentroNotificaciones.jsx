// Centro de notificaciones de la cabecera y los avisos push.
//
// Cada aviso lo crea ms-notificaciones al consumir un evento de las colas (SQS y RabbitMQ). Aquí
// se consultan cada 20 segundos: un aviso que no estaba en la consulta anterior acaba de llegar,
// y si la persona tiene los push activos, aparece también como tarjeta emergente.
import { useCallback, useEffect, useRef, useState } from 'react'
import { colaboracionesRecibidas, listarAvisos, marcarAvisoLeido } from './api'
import { alCambiar, avisarCambio } from './eventos'
import { fechaLarga, fechaRelativa, tipoDeAviso } from './catalogo'
import { PasosSolicitud } from './Solicitudes'

const CADA = 20_000
/** Cuánto dura un aviso push según la preferencia: 6 s, 12 s o hasta que la persona lo cierre. */
export const DURACIONES_PUSH = [
  { texto: '6 s', ms: 6_000 },
  { texto: '12 s', ms: 12_000 },
  { texto: 'Hasta cerrarlos', ms: null },
]
/** Se muestran a la vez hasta tres; el resto queda en "Ver todos". */
const MAX_EMERGENTES = 3
const PESTANAS = [
  { id: 'todas', texto: 'Todas' },
  { id: 'sinleer', texto: 'Sin leer' },
  { id: 'solicitudes', texto: 'Solicitudes' },
]

/**
 * Avisos y solicitudes pendientes, al día. Lo usa la cabecera: el contador, el panel y los push
 * salen de aquí. `push` dice si los avisos nuevos se muestran como tarjeta emergente.
 */
export function useNotificaciones({ push }) {
  const [avisos, setAvisos] = useState([])
  const [pendientes, setPendientes] = useState([])
  const [emergentes, setEmergentes] = useState([])
  const conocidos = useRef(null)   // ids ya vistos; null = todavía no hubo primera carga
  const pushActivo = useRef(push)
  pushActivo.current = push

  const cerrarEmergente = useCallback((id) => setEmergentes((lista) => lista.filter((a) => a.id !== id)), [])

  const recargarPendientes = useCallback(() => {
    colaboracionesRecibidas()
      .then((todas) => setPendientes(todas.filter((s) => s.estado === 'PENDIENTE')))
      .catch(() => setPendientes([]))
  }, [])

  const recargar = useCallback(() => {
    listarAvisos().then((lista) => {
      const nuevos = conocidos.current === null ? [] : lista.filter((a) => !conocidos.current.has(a.id))
      conocidos.current = new Set(lista.map((a) => a.id))
      setAvisos(lista)
      if (nuevos.length === 0) return
      // Un aviso de colaboración cambia las bandejas: se recargan aquí y en el perfil.
      if (nuevos.some((a) => a.tipo?.startsWith('colaboracion.'))) {
        recargarPendientes()
        avisarCambio('colaboraciones')
      }
      // Cada tarjeta lleva su propio reloj (ver Emergente): así se puede detener con el mouse.
      if (pushActivo.current) setEmergentes((actuales) => [...nuevos, ...actuales].slice(0, 20))
    }).catch(() => { /* sin avisos no se corta la aplicación; se reintenta en la próxima vuelta */ })
  }, [recargarPendientes])

  useEffect(() => {
    recargar()
    recargarPendientes()
    const reloj = setInterval(recargar, CADA)
    // Al volver a la pestaña no se espera la próxima vuelta: lo nuevo se ve de inmediato.
    window.addEventListener('focus', recargar)
    const dejar = alCambiar('colaboraciones', recargarPendientes)
    return () => { clearInterval(reloj); window.removeEventListener('focus', recargar); dejar() }
  }, [recargar, recargarPendientes])

  const marcar = useCallback(async (aviso) => {
    if (aviso.leido) return
    setAvisos((lista) => lista.map((a) => (a.id === aviso.id ? { ...a, leido: true } : a)))
    await marcarAvisoLeido(aviso.id).catch(recargar)
  }, [recargar])

  // No hay ruta para marcar todos: se marcan de a uno. Son pocos y el servidor es el mismo.
  const marcarTodos = useCallback(async () => {
    const sinLeer = avisos.filter((a) => !a.leido)
    setAvisos((lista) => lista.map((a) => ({ ...a, leido: true })))
    await Promise.all(sinLeer.map((a) => marcarAvisoLeido(a.id))).catch(recargar)
  }, [avisos, recargar])

  const cerrarEmergentes = useCallback(() => setEmergentes([]), [])

  return {
    avisos, pendientes, emergentes,
    noLeidos: avisos.filter((a) => !a.leido).length,
    marcar, marcarTodos, cerrarEmergente, cerrarEmergentes, recargarPendientes,
  }
}

/**
 * Botón con el contador y el panel desplegable. Las solicitudes pendientes van arriba: son lo
 * único que le pide una respuesta a la persona.
 */
export function CentroNotificaciones({ notificaciones, abierto, alAlternar, alIr, alAceptar, alRechazar }) {
  const [pestana, setPestana] = useState('todas')
  const { avisos, pendientes, noLeidos, marcar, marcarTodos } = notificaciones

  const visibles = avisos.filter((a) => (
    pestana === 'todas' ? true : pestana === 'sinleer' ? !a.leido : a.tipo === 'colaboracion.solicitada'))

  const abrir = (aviso) => {
    marcar(aviso)
    alIr(aviso)
  }

  return (
    <div className="desplegable">
      <button
        type="button"
        className="boton-barra"
        onClick={alAlternar}
        aria-expanded={abierto}
        aria-haspopup="dialog"
        aria-keyshortcuts="Alt+N"
        aria-label={`Notificaciones, ${noLeidos} sin leer`}
        title="Notificaciones (Alt+N)"
      >
        <span className="icono-campana" aria-hidden="true" />
        Notificaciones
        <span className={noLeidos ? 'contador hay' : 'contador'} aria-hidden="true">{noLeidos}</span>
      </button>

      {abierto && (
        <div className="panel-flotante notificaciones" role="dialog" aria-label="Centro de notificaciones">
          <div className="notif-cabecera">
            <div className="notif-titulo">
              <h2>Notificaciones</h2>
              <button type="button" className="enlace" onClick={marcarTodos} disabled={noLeidos === 0}>
                Marcar todo como leído
              </button>
            </div>
            <div className="chips" role="tablist" aria-label="Filtrar notificaciones">
              {PESTANAS.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  role="tab"
                  aria-selected={pestana === p.id}
                  className={pestana === p.id ? 'chip-filtro activo' : 'chip-filtro'}
                  onClick={() => setPestana(p.id)}
                >
                  {p.texto}
                </button>
              ))}
            </div>
          </div>

          {pendientes.length > 0 && (
            <div className="notif-pendientes">
              <p className="rotulo">Requiere tu respuesta</p>
              {pendientes.map((s) => (
                <div key={s.id} className="notif-pendiente">
                  <p className="persona">{s.solicitante?.nombre ?? 'Alguien de la comunidad'}</p>
                  <p className="detalle">
                    quiere colaborar{s.proyectoNombre ? ` en ${s.proyectoNombre}` : ''} · {fechaLarga(s.fechaSolicitud)}
                  </p>
                  <PasosSolicitud estado={s.estado} esRecibida />
                  <div className="acciones">
                    <button type="button" className="principal crece" onClick={() => alAceptar(s)}>Aceptar</button>
                    <button type="button" className="secundario crece" onClick={() => alRechazar(s)}>Rechazar</button>
                  </div>
                </div>
              ))}
            </div>
          )}

          <ul className="notif-lista">
            {visibles.map((a) => {
              const tipo = tipoDeAviso(a.tipo)
              return (
                <li key={a.id} className={a.leido ? 'notif' : 'notif no-leida'}>
                  <span className={`notif-barra ${tipo.clase}`} aria-hidden="true" />
                  <div className="notif-cuerpo">
                    <div className="notif-meta">
                      <span className="notif-tipo">{tipo.etiqueta}</span>
                      <span>{fechaRelativa(a.creadoEn)}</span>
                      {!a.leido && <span className="punto-sin-leer" role="img" aria-label="Sin leer" />}
                    </div>
                    <button type="button" className="notif-abrir" onClick={() => abrir(a)}>
                      <span className="titulo">{a.titulo}</span>
                      {a.detalle && <span className="texto">{a.detalle}</span>}
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>
          {visibles.length === 0 && <p className="ayuda notif-vacio">No hay notificaciones en esta vista.</p>}

          <div className="notif-pie">
            <p>
              Los avisos llegan de forma asíncrona (SNS/SQS y RabbitMQ) y pueden tardar unos
              segundos en aparecer.
            </p>
            <button type="button" className="secundario chico" onClick={() => alIr({ destino: 'preferencias' })}>
              Preferencias
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * Tarjetas emergentes de los avisos recién llegados. Se ven hasta tres; si llegan más, un botón
 * abre el centro con todos. `duracion` en milisegundos, o null para que esperen a que las cierren.
 */
export function AvisosEmergentes({ emergentes, duracion, alCerrar, alIr, alVerTodos }) {
  const restantes = emergentes.length - MAX_EMERGENTES
  return (
    <div className="emergentes" role="region" aria-label="Avisos nuevos" aria-live="polite">
      {emergentes.slice(0, MAX_EMERGENTES).map((a) => (
        <Emergente key={a.id} aviso={a} duracion={duracion} alCerrar={alCerrar} alIr={alIr} />
      ))}
      {restantes > 0 && (
        <button type="button" className="ver-todos-emergentes" onClick={alVerTodos}>
          Ver todos ({emergentes.length})
        </button>
      )}
    </div>
  )
}

/**
 * Un aviso emergente con su reloj. Pasar el mouse o el foco lo detiene, y al salir sigue con el
 * tiempo que le quedaba: así nadie pierde un aviso por estar leyéndolo.
 */
function Emergente({ aviso, duracion, alCerrar, alIr }) {
  const [detenido, setDetenido] = useState(false)
  const restante = useRef(duracion)
  const tipo = tipoDeAviso(aviso.tipo)

  useEffect(() => {
    if (duracion == null || detenido) return undefined
    const inicio = Date.now()
    const reloj = setTimeout(() => alCerrar(aviso.id), restante.current)
    return () => { clearTimeout(reloj); restante.current -= Date.now() - inicio }
  }, [duracion, detenido, aviso.id, alCerrar])

  const detener = () => setDetenido(true)
  // El foco puede moverse entre los botones de la misma tarjeta: solo se reanuda si sale de ella.
  const seguir = (e) => { if (!e.currentTarget.contains(e.relatedTarget)) setDetenido(false) }

  return (
    <div
      className={['emergente', duracion && `vida-${duracion / 1000}`, detenido && 'detenido'].filter(Boolean).join(' ')}
      onMouseEnter={detener}
      onMouseLeave={() => setDetenido(false)}
      onFocus={detener}
      onBlur={seguir}
    >
      <span className={`emergente-icono ${tipo.clase}`} aria-hidden="true" />
      <div className="emergente-cuerpo">
        <p className="notif-tipo">{tipo.etiqueta} · ahora</p>
        <p className="titulo">{aviso.titulo}</p>
        {aviso.detalle && <p className="texto">{aviso.detalle}</p>}
        <button type="button" className="principal chico" onClick={() => { alCerrar(aviso.id); alIr(aviso) }}>
          {tipo.accion}
        </button>
      </div>
      <button type="button" className="cerrar-emergente" onClick={() => alCerrar(aviso.id)} aria-label="Descartar aviso">×</button>
      {duracion && <span className={`emergente-vida ${tipo.clase}`} aria-hidden="true" />}
    </div>
  )
}
