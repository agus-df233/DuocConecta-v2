// Vitrina de proyectos: la pantalla principal.
// Desde aquí se publica, se filtra, se abre el detalle, se comparte y se pide colaborar.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  listarProyectos, publicarProyecto, editarProyecto, eliminarProyecto,
  solicitarColaboracion, colaboracionesEnviadas,
} from './api'
import { claimsDelToken } from './auth'
import Aviso, { useAviso } from './Aviso'
import CompartirProyecto from './CompartirProyecto'
import Confirmar from './Confirmar'
import DetalleProyecto from './DetalleProyecto'
import { alCambiar } from './eventos'
import { escuelaDe } from './catalogo'
import FiltrosVitrina, { SIN_FILTROS } from './FiltrosVitrina'
import FormularioProyecto from './FormularioProyecto'
import PedirContacto from './PedirContacto'
import TarjetaProyecto from './TarjetaProyecto'

// El feed en vivo vuelve a pedir la vitrina cada 20 segundos: lo publicado por otras personas
// aparece solo, marcado como nuevo, sin recargar la página.
const CADA = 20_000

export default function Vitrina({ foco, alUsarFoco, escondidos, alEsconder }) {
  const [proyectos, setProyectos] = useState(null)
  const [miOid, setMiOid] = useState(null)
  const [error, setError] = useState(null)
  const { texto: aviso, accion: accionAviso, avisar, cerrar: cerrarAviso } = useAviso()

  const [filtros, setFiltros] = useState(SIN_FILTROS)
  const [enVivo, setEnVivo] = useState(true)
  const [nuevos, setNuevos] = useState(new Set())
  const conocidos = useRef(null)

  const [abierto, setAbierto] = useState(null)       // { id, comentar }
  const [editando, setEditando] = useState(null)     // null cerrado · {} nuevo · proyecto si edita
  const [pidiendoA, setPidiendoA] = useState(null)   // el proyecto cuyo autor se quiere contactar
  const [compartiendo, setCompartiendo] = useState(null)
  const [borrando, setBorrando] = useState(null)

  // Estado de mi solicitud por cada proyecto: PENDIENTE o ACEPTADA. Va por proyecto y no por
  // persona, porque alguien puede tener varios proyectos y se pide colaborar por cada uno.
  const [estadoPorProyecto, setEstadoPorProyecto] = useState(new Map())

  // El oid dice qué proyectos son propios. Sale del token, no del perfil, que es editable.
  useEffect(() => {
    claimsDelToken().then((c) => setMiOid(c.oid ?? c.sub)).catch(() => setMiOid(null))
  }, [])

  /** Pide la vitrina. Lo que no estaba en la carga anterior se marca como nuevo. */
  const cargarProyectos = useCallback(() => listarProyectos().then((lista) => {
    if (conocidos.current) {
      const recien = lista.filter((p) => !conocidos.current.has(p.id)).map((p) => p.id)
      if (recien.length) setNuevos((actuales) => new Set([...actuales, ...recien]))
    }
    conocidos.current = new Set(lista.map((p) => p.id))
    setProyectos(lista)
  }), [])

  const cargarSolicitudes = useCallback(() => {
    // Una solicitud rechazada o retirada no bloquea para siempre: solo cuentan las que siguen en pie.
    // Si esta llamada falla no se puede saber a quién ya se le pidió, así que se avisa en vez
    // de dejar el botón habilitado y que el servidor rechace la solicitud repetida.
    colaboracionesEnviadas()
      .then((enviadas) => setEstadoPorProyecto(new Map(
        enviadas.filter((s) => (s.estado === 'PENDIENTE' || s.estado === 'ACEPTADA') && s.proyectoId)
          .map((s) => [s.proyectoId, s.estado]))))
      .catch((e) => {
        setEstadoPorProyecto(new Map())
        setError(`No se pudieron leer tus solicitudes enviadas: ${e.message}`)
      })
  }, [])

  const recargar = useCallback(() => {
    setError(null)
    cargarProyectos().catch((e) => { setProyectos((p) => p ?? []); setError(e.message) })
    cargarSolicitudes()
  }, [cargarProyectos, cargarSolicitudes])

  useEffect(() => { recargar() }, [recargar])
  useEffect(() => alCambiar('colaboraciones', cargarSolicitudes), [cargarSolicitudes])

  useEffect(() => {
    if (!enVivo) return undefined
    const reloj = setInterval(() => cargarProyectos().catch(() => { /* se reintenta en la próxima vuelta */ }), CADA)
    return () => clearInterval(reloj)
  }, [enVivo, cargarProyectos])

  // Un aviso o un enlace compartido piden abrir un proyecto: se abre en cuanto la vitrina cargó.
  useEffect(() => {
    if (!foco?.proyectoId || proyectos === null) return
    if (proyectos.some((p) => p.id === foco.proyectoId)) setAbierto({ id: foco.proyectoId })
    else setError('Ese proyecto ya no está en la vitrina: lo ocultaron o lo eliminaron.')
    alUsarFoco()
  }, [foco, proyectos, alUsarFoco])

  const guardar = async (datos) => {
    try {
      if (datos.id) {
        await editarProyecto(datos.id, datos)
        avisar('Proyecto actualizado.')
      } else {
        await publicarProyecto(datos)
        avisar('Proyecto publicado en la vitrina.')
      }
      setEditando(null)
      recargar()
    } catch (e) {
      setError(e.message)
    }
  }

  const borrar = async () => {
    try {
      await eliminarProyecto(borrando.id)
      setAbierto(null)
      avisar('Proyecto eliminado.')
      recargar()
    } catch (e) {
      setError(e.message)
    } finally {
      setBorrando(null)
    }
  }

  /** Envía la solicitud con el mensaje y la decisión sobre el teléfono. */
  const enviarSolicitud = async (mensaje, compartirTelefono) => {
    try {
      await solicitarColaboracion({
        solicitadoId: pidiendoA.propietarioId,
        proyectoId: pidiendoA.id,
        mensaje,
        compartirTelefono,
      })
      setEstadoPorProyecto(new Map(estadoPorProyecto).set(pidiendoA.id, 'PENDIENTE'))
      avisar(`Solicitud enviada a ${pidiendoA.autor?.nombre?.split(' ')[0] ?? 'quien lo publicó'}. Te avisamos cuando responda.`)
    } catch (e) {
      setError(e.message)
    } finally {
      setPidiendoA(null)
    }
  }

  // Esconder se puede deshacer: es fácil tocar el botón sin querer y la tarjeta desaparece.
  const esconder = (proyecto) => {
    const antes = escondidos
    alEsconder([...escondidos, proyecto.id])
    avisar(`"${proyecto.nombre}" ya no aparece en tu vitrina. Sigue publicado para el resto.`,
      { texto: 'Deshacer', alTocar: () => alEsconder(antes) })
  }

  const escondidosAqui = useMemo(
    () => (proyectos ?? []).filter((p) => escondidos.includes(p.id)).length,
    [proyectos, escondidos])
  const disponibles = useMemo(
    () => (proyectos ?? []).filter((p) => !escondidos.includes(p.id)),
    [proyectos, escondidos])
  const visibles = useMemo(() => filtrar(disponibles, filtros), [disponibles, filtros])
  const proyectoAbierto = proyectos?.find((p) => p.id === abierto?.id) ?? null

  if (proyectos === null) return <EsqueletoVitrina />

  return (
    <section>
      <div className="titulo-pagina">
        <div>
          <h1>Vitrina de proyectos</h1>
          <p>Lo que se está construyendo ahora en Duoc UC.</p>
        </div>
        <div className="acciones-pagina">
          <button
            type="button"
            className={enVivo ? 'boton-vivo activo' : 'boton-vivo'}
            aria-pressed={enVivo}
            onClick={() => setEnVivo(!enVivo)}
            title="Con el feed activo, lo que publiquen otras personas aparece solo"
          >
            <span className="punto-vivo" aria-hidden="true" />
            {enVivo ? 'Feed en vivo activo' : 'Feed en vivo pausado'}
          </button>
          <button type="button" className="principal" onClick={() => setEditando({})}>Publicar proyecto</button>
        </div>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      <FiltrosVitrina
        filtros={filtros}
        alCambiar={setFiltros}
        proyectos={disponibles}
        coinciden={visibles.length}
      />

      {escondidosAqui > 0 && (
        <div className="aviso escondidos">
          <p>
            {escondidosAqui === 1
              ? '1 proyecto escondido de tu vitrina. Sigue publicado para el resto.'
              : `${escondidosAqui} proyectos escondidos de tu vitrina. Siguen publicados para el resto.`}
          </p>
          <button type="button" className="enlace" onClick={() => alEsconder([])}>Mostrar todos</button>
        </div>
      )}

      {visibles.length === 0 ? (
        <div className="vacio">
          <div className="logo" aria-hidden="true" />
          <h3>{proyectos.length === 0 ? 'Aún no hay proyectos publicados' : 'Todavía no hay proyectos con esa combinación'}</h3>
          <p className="ayuda">
            {proyectos.length === 0
              ? 'Publica el tuyo y serás el primero en aparecer.'
              : 'Prueba con menos filtros, o publica el primero con ese perfil y deja que te encuentren.'}
          </p>
          <div className="acciones centradas">
            {proyectos.length > 0 && (
              <button type="button" className="secundario" onClick={() => setFiltros(SIN_FILTROS)}>Limpiar filtros</button>
            )}
            <button type="button" className="principal" onClick={() => setEditando({})}>Publicar proyecto</button>
          </div>
        </div>
      ) : (
        <div className="grilla">
          {visibles.map((p) => (
            <TarjetaProyecto
              key={p.id}
              proyecto={p}
              esMio={p.propietarioId === miOid}
              esNuevo={nuevos.has(p.id)}
              estadoSolicitud={estadoPorProyecto.get(p.id)}
              alAbrir={() => setAbierto({ id: p.id })}
              alComentar={() => setAbierto({ id: p.id, comentar: true })}
              alColaborar={() => setPidiendoA(p)}
              alEditar={() => setEditando(p)}
              alEsconder={() => esconder(p)}
            />
          ))}
        </div>
      )}

      {proyectoAbierto && (
        <DetalleProyecto
          proyecto={proyectoAbierto}
          esMio={proyectoAbierto.propietarioId === miOid}
          estadoSolicitud={estadoPorProyecto.get(proyectoAbierto.id)}
          enfocarComentario={abierto.comentar}
          alColaborar={() => { setPidiendoA(proyectoAbierto); setAbierto(null) }}
          alCompartir={() => setCompartiendo(proyectoAbierto)}
          alEditar={() => { setEditando(proyectoAbierto); setAbierto(null) }}
          alBorrar={() => setBorrando(proyectoAbierto)}
          alCerrar={() => setAbierto(null)}
        />
      )}

      {pidiendoA && (
        <PedirContacto proyecto={pidiendoA} alEnviar={enviarSolicitud} alCerrar={() => setPidiendoA(null)} />
      )}

      {compartiendo && (
        <CompartirProyecto proyecto={compartiendo} alCerrar={() => setCompartiendo(null)} alAvisar={avisar} />
      )}

      {borrando && (
        <Confirmar
          titulo="Eliminar proyecto"
          textoAccion="Eliminar"
          alConfirmar={borrar}
          alCerrar={() => setBorrando(null)}
        >
          Se borra <strong>{borrando.nombre}</strong> con sus comentarios y archivos. No se puede deshacer.
          Si solo quieres sacarlo de la vitrina, ocúltalo desde tu perfil.
        </Confirmar>
      )}

      {editando && (
        <FormularioProyecto
          proyecto={editando.id ? editando : null}
          alGuardar={guardar}
          alCerrar={() => setEditando(null)}
        />
      )}

      <Aviso texto={aviso} accion={accionAviso} alCerrar={cerrarAviso} />
    </section>
  )
}

/** Tarjetas grises mientras carga: la página no salta cuando llegan los datos. */
function EsqueletoVitrina() {
  return (
    <section aria-busy="true" aria-label="Cargando la vitrina">
      <div className="titulo-pagina">
        <div>
          <h1>Vitrina de proyectos</h1>
          <p>Lo que se está construyendo ahora en Duoc UC.</p>
        </div>
      </div>
      <div className="grilla">
        {[1, 2, 3, 4, 5, 6].map((n) => (
          <div key={n} className="tarjeta esqueleto" aria-hidden="true">
            <span className="linea corta" />
            <span className="linea titulo" />
            <span className="linea" />
            <span className="linea" />
            <span className="linea media" />
          </div>
        ))}
      </div>
    </section>
  )
}

/**
 * Los filtros son opcionales y se combinan. Escuela y carrera son excluyentes: el mismo
 * desplegable pone uno u otro. No hay filtro de sede porque hoy la plataforma tiene una sola.
 */
function filtrar(proyectos, filtros) {
  const buscado = filtros.texto.trim().toLowerCase()

  return proyectos.filter((p) => {
    const texto = `${p.nombre} ${p.resumen ?? ''} ${p.descripcion ?? ''}`.toLowerCase()
    return (!buscado || texto.includes(buscado))
      && (!filtros.escuela || escuelaDe(p.autor?.carrera) === filtros.escuela)
      && (!filtros.carrera || p.autor?.carrera === filtros.carrera)
      && (!filtros.herramienta || p.herramientas?.includes(filtros.herramienta))
      && (!filtros.estado || p.estado === filtros.estado)
  })
}
