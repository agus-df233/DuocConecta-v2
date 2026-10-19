// Pantalla de perfil: datos propios, solicitudes, colaboraciones, preferencias de notificación y
// proyectos publicados. Las solicitudes están aquí y no en una vista aparte porque son información privada.
import { useEffect, useMemo, useState } from 'react'
import {
  obtenerMiPerfil, guardarMiPerfil, alternarVisibilidad,
  listarProyectos, editarProyecto, retirarColaboracion,
} from './api'
import { claimsDelToken } from './auth'
import Aviso, { useAviso } from './Aviso'
import Confirmar from './Confirmar'
import { avisarCambio } from './eventos'
import FormularioProyecto from './FormularioProyecto'
import AceptarSolicitud from './AceptarSolicitud'
import Interruptor from './Interruptor'
import { MisColaboraciones, MisSolicitudes, responder, useColaboraciones } from './Solicitudes'
import { ESCUELAS, ESTADOS, SEDES, SIN_CARRERA, iniciales } from './catalogo'

// Qué sección del perfil corresponde a cada destino de un aviso.
const SECCIONES = {
  recibidas: 'seccion-solicitudes',
  enviadas: 'seccion-solicitudes',
  colaboraciones: 'seccion-colaboraciones',
  preferencias: 'seccion-preferencias',
  'mis-proyectos': 'seccion-proyectos',
}

export default function Perfil({ foco, alUsarFoco, preferencias, cambiarPreferencias }) {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(null)
  const { texto: aviso, avisar } = useAviso()
  const [pestana, setPestana] = useState('recibidas')

  const colaboraciones = useColaboraciones()

  useEffect(() => {
    obtenerMiPerfil().then(setDatos).catch((e) => setError(e.message))
  }, [])

  // Un aviso puede traer a una sección: se elige la pestaña y se baja hasta ella cuando ya se pintó.
  useEffect(() => {
    if (!foco?.seccion || !datos) return
    if (foco.seccion === 'recibidas' || foco.seccion === 'enviadas') setPestana(foco.seccion)
    const destino = document.getElementById(SECCIONES[foco.seccion] ?? '')
    destino?.scrollIntoView({ behavior: preferencias.movimiento ? 'auto' : 'smooth', block: 'start' })
    destino?.focus({ preventScroll: true })
    alUsarFoco()
  }, [foco, datos, alUsarFoco, preferencias.movimiento])

  // Aceptar abre la ventana de consentimiento; rechazar no comparte nada y va directo.
  const [aceptando, setAceptando] = useState(null)
  // Retirar pide confirmación: la otra persona ya no podrá responder.
  const [retirando, setRetirando] = useState(null)

  const retirarSolicitud = async () => {
    try {
      await retirarColaboracion(retirando.id)
      avisar('Retiraste tu solicitud. Puedes volver a pedirla cuando quieras.')
      // Recarga las bandejas aquí, el botón de la vitrina y las pendientes de la cabecera.
      avisarCambio('colaboraciones')
    } catch (e) {
      setError(e.message)
    } finally {
      setRetirando(null)
    }
  }

  const responderSolicitud = async (solicitud, aceptar, compartirTelefono = false) => {
    try {
      await responder(solicitud, aceptar, compartirTelefono)
      avisar(aceptar
        ? 'Aceptaste la solicitud: ya puede ver tus datos de contacto.'
        : 'Rechazaste la solicitud. No se compartió ningún dato.')
      colaboraciones.recargar()
    } catch (e) {
      setError(e.message)
    } finally {
      setAceptando(null)
    }
  }

  if (error && !datos) return <div className="error" role="alert">{error}</div>
  if (!datos) return <div className="cargando" aria-busy="true">Cargando tu perfil…</div>

  const { perfil } = datos

  return (
    <section>
      <div className="cabecera-perfil">
        <div className="marca-grande" aria-hidden="true">{iniciales(perfil.nombre)}</div>
        <div>
          <h1>{perfil.nombre}</h1>
          <p>{[perfil.carrera, perfil.sede && `Sede ${perfil.sede}`].filter(Boolean).join(' · ') || 'Completa tu carrera y sede'}</p>
        </div>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="columnas">
        <MisDatos datos={datos} alGuardar={setDatos} alAvisar={avisar} alFallar={setError} />

        <section className="tarjeta" id="seccion-solicitudes" tabIndex={-1} aria-labelledby="titulo-solicitudes">
          <h2 id="titulo-solicitudes">Mis solicitudes</h2>
          {colaboraciones.error && <div className="error" role="alert">{colaboraciones.error}</div>}
          <MisSolicitudes
            pestana={pestana}
            alCambiarPestana={setPestana}
            recibidas={colaboraciones.recibidas}
            enviadas={colaboraciones.enviadas}
            alResponder={(s, aceptar) =>
              (aceptar ? setAceptando(s) : responderSolicitud(s, false))}
            alRetirar={setRetirando}
          />

          <div id="seccion-colaboraciones" tabIndex={-1}>
            <h2 className="titulo-seguido">Mis colaboraciones</h2>
            <p className="ayuda">Contactos desbloqueados por consentimiento mutuo.</p>
            <MisColaboraciones recibidas={colaboraciones.recibidas} enviadas={colaboraciones.enviadas} />
          </div>
        </section>

        <PreferenciasDeAviso preferencias={preferencias} alCambiar={cambiarPreferencias} />

        <MisProyectos alAvisar={avisar} alFallar={setError} />
      </div>

      {aceptando && (
        <AceptarSolicitud
          solicitud={aceptando}
          alConfirmar={(compartirTelefono) =>
            responderSolicitud(aceptando, true, compartirTelefono)}
          alCerrar={() => setAceptando(null)}
        />
      )}

      {retirando && (
        <Confirmar
          titulo="Retirar solicitud"
          textoAccion="Retirar solicitud"
          alConfirmar={retirarSolicitud}
          alCerrar={() => setRetirando(null)}
        >
          {retirando.solicitado?.nombre?.split(' ')[0] ?? 'La otra persona'} ya no podrá responderla y
          tus datos de contacto no se compartirán. Si cambias de idea, puedes volver a pedirla.
        </Confirmar>
      )}

      <Aviso texto={aviso} />
    </section>
  )
}

/** Nombre, correo y rol vienen del token y no se editan. Lo demás lo completa la persona. */
function MisDatos({ datos, alGuardar, alAvisar, alFallar }) {
  const { perfil, redes } = datos
  const [guardando, setGuardando] = useState(false)

  const enviar = async (evento) => {
    evento.preventDefault()
    setGuardando(true)
    const f = new FormData(evento.target)
    try {
      await guardarMiPerfil({
        carrera: f.get('carrera'),
        sede: f.get('sede'),
        bio: f.get('bio'),
        telefono: f.get('telefono'),
        // Una red por línea, que es más cómodo de escribir que un JSON.
        redes: f.get('redes').split('\n').map((r) => r.trim()).filter(Boolean),
      })
      alGuardar(await obtenerMiPerfil())
      alAvisar('Perfil actualizado.')
    } catch (e) {
      alFallar(e.message)
    } finally {
      setGuardando(false)
    }
  }

  const cambiarVisibilidad = async () => {
    try {
      await alternarVisibilidad()
      const frescos = await obtenerMiPerfil()
      alGuardar(frescos)
      alAvisar(frescos.perfil.visible
        ? 'Tu perfil vuelve a aparecer en las búsquedas.'
        : 'Tu perfil quedó oculto de las búsquedas.')
    } catch (e) {
      alFallar(e.message)
    }
  }

  return (
    <section className="tarjeta" aria-labelledby="titulo-datos">
      <h2 id="titulo-datos">Mis datos</h2>

      <form onSubmit={enviar}>
        <label>
          Nombre
          <input value={perfil.nombre} disabled />
          <span className="ayuda">Lo administra Duoc y llega con tu inicio de sesión.</span>
        </label>

        <label>
          Correo institucional
          <input value={perfil.correo} disabled />
        </label>

        <div className="dos-columnas">
          {/* Desplegable y no texto libre: si la misma carrera se escribe de tres formas
              distintas, el filtro por escuela de la vitrina deja de agrupar bien. */}
          <label>
            Carrera
            <select name="carrera" defaultValue={perfil.carrera ?? ''}>
              <option value="">Elige tu carrera</option>
              {ESCUELAS.map((esc) => (
                <optgroup key={esc.nombre} label={esc.nombre}>
                  {esc.carreras.map((c) => <option key={c} value={c}>{c}</option>)}
                </optgroup>
              ))}
              <option value={SIN_CARRERA}>{SIN_CARRERA}</option>
            </select>
          </label>
          <label>
            Sede
            <select name="sede" defaultValue={perfil.sede ?? SEDES[0]}>
              {SEDES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
        </div>

        <label>
          Biografía
          <textarea name="bio" rows={3} defaultValue={perfil.bio ?? ''} />
        </label>

        {/* Este bloque es la promesa central de la plataforma, así que se explica donde se
            escriben los datos y no en una ayuda perdida en otra pantalla. */}
        <div className="consentimiento">
          <h4><span className="candado" aria-hidden="true" />Datos que se comparten solo cuando aceptas</h4>
          <p>
            Tu teléfono y tus redes no aparecen en la vitrina ni en las búsquedas. Se muestran
            únicamente a las personas cuya solicitud de colaboración aceptas.
          </p>
          <label>
            Teléfono (opcional)
            <input name="telefono" placeholder="+56 9 …" defaultValue={perfil.telefono ?? ''} />
          </label>
          <label>
            Redes (opcional, una por línea)
            <textarea name="redes" rows={2} placeholder="@usuario" defaultValue={redes.join('\n')} />
          </label>
        </div>

        <div className="acciones">
          <button className="principal" type="submit" disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar cambios'}
          </button>
        </div>
      </form>

      <Interruptor
        tarjeta
        activo={!perfil.visible}
        alCambiar={cambiarVisibilidad}
        texto="Ocultar mi perfil"
        descripcion={perfil.visible
          ? 'Tu perfil aparece en las búsquedas. Actívalo para ocultarlo.'
          : 'Tu perfil está oculto: no apareces en las búsquedas ni en los listados de personas.'}
      />
    </section>
  )
}

/**
 * Por dónde avisamos. El aviso en la plataforma no se apaga: es donde quedan las solicitudes. Los
 * push son las tarjetas emergentes mientras la aplicación está abierta. El correo llega con SES en
 * una entrega siguiente y por eso no aparece aquí.
 */
function PreferenciasDeAviso({ preferencias, alCambiar }) {
  return (
    <section className="tarjeta ancho-completo" id="seccion-preferencias" tabIndex={-1} aria-labelledby="titulo-preferencias">
      <h2 id="titulo-preferencias">Notificaciones</h2>
      <p className="ayuda">
        Elige cómo te avisamos cuando alguien quiere colaborar, responde tu solicitud o comenta tus proyectos.
      </p>
      <div className="canales">
        <Interruptor
          tarjeta
          activo
          deshabilitado
          alCambiar={() => {}}
          texto="En la plataforma"
          descripcion="Centro de notificaciones y contador. Siempre activo."
        />
        <Interruptor
          tarjeta
          activo={preferencias.push}
          alCambiar={(push) => alCambiar({ push })}
          texto="Avisos push"
          descripcion="Tarjeta emergente mientras usas DuocConecta."
        />
      </div>
      <div className="estados-aviso">
        <span>Cada aviso pasa por tres estados:</span>
        <span className="paso">Publicado</span>
        <span className="paso">En cola</span>
        <span className="paso">Entregado</span>
        <span>La entrega es asíncrona y puede tardar unos segundos.</span>
      </div>
    </section>
  )
}

/**
 * Los proyectos propios, incluidos los ocultos. Ocultar cambia la visibilidad, no borra. Un
 * proyecto oculto por moderación solo lo ve su dueña, con el motivo, y ella no puede revertirlo.
 */
function MisProyectos({ alAvisar, alFallar }) {
  const [todos, setTodos] = useState(null)
  const [miOid, setMiOid] = useState(null)
  const [editando, setEditando] = useState(null)

  // El oid del token es lo que dice qué proyectos son propios.
  useEffect(() => {
    claimsDelToken().then((c) => setMiOid(c.oid ?? c.sub)).catch(() => setMiOid(null))
  }, [])

  useEffect(() => { recargar() }, [])

  function recargar() {
    listarProyectos().then(setTodos).catch(() => setTodos([]))
  }

  const mios = useMemo(
    // Los abiertos no son "míos" aunque los haya subido el administrador: se ven como de DuocConecta.
    () => (todos ?? []).filter((p) => p.propietarioId === miOid && !p.abierto),
    [todos, miOid])

  const cambiarVisibilidad = async (proyecto) => {
    const visibilidad = proyecto.visibilidad === 'PUBLICO' ? 'PRIVADO' : 'PUBLICO'
    try {
      await editarProyecto(proyecto.id, { ...proyecto, visibilidad })
      alAvisar(visibilidad === 'PRIVADO'
        ? 'El proyecto ya no aparece en la vitrina.'
        : 'El proyecto volvió a la vitrina.')
      recargar()
    } catch (e) {
      alFallar(e.message)
    }
  }

  const guardar = async (datos) => {
    try {
      await editarProyecto(datos.id, datos)
      setEditando(null)
      alAvisar('Proyecto actualizado.')
      recargar()
    } catch (e) {
      alFallar(e.message)
    }
  }

  return (
    <section className="tarjeta ancho-completo" id="seccion-proyectos" tabIndex={-1} aria-labelledby="titulo-proyectos">
      <h2 id="titulo-proyectos">Mis proyectos</h2>
      <p className="ayuda">Ocultar saca el proyecto de la vitrina pública, pero lo conservas aquí.</p>

      {todos === null ? (
        <p className="ayuda">Cargando…</p>
      ) : mios.length === 0 ? (
        <p className="ayuda">Aún no publicas nada. Se publica desde la vitrina.</p>
      ) : (
        <div className="grilla">
          {mios.map((p) => (
            <div key={p.id} className={p.visibilidad === 'PUBLICO' && !p.oculto ? 'tarjeta mio' : 'tarjeta mio apagado'}>
              <div className="etiquetas">
                <span className={`insignia ${p.estado}`}>{ESTADOS[p.estado] ?? p.estado}</span>
                {p.oculto ? (
                  <span className="pildora moderado">Oculto por moderación</span>
                ) : (
                  <span className={p.visibilidad === 'PUBLICO' ? 'pildora visible' : 'pildora'}>
                    {p.visibilidad === 'PUBLICO' ? 'Visible' : 'Oculto'}
                  </span>
                )}
              </div>
              <h3>{p.nombre}</h3>
              <p className="ayuda">{p.resumen}</p>
              {p.oculto && p.motivoOcultamiento && (
                <p className="motivo-moderacion">Motivo: {p.motivoOcultamiento}</p>
              )}
              <div className="acciones">
                <button type="button" className="secundario" onClick={() => setEditando(p)}>Editar</button>
                {!p.oculto && (
                  <button type="button" className="secundario" onClick={() => cambiarVisibilidad(p)}>
                    {p.visibilidad === 'PUBLICO' ? 'Ocultar de la vitrina' : 'Hacer visible'}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {editando && (
        <FormularioProyecto
          proyecto={editando}
          alGuardar={guardar}
          alCerrar={() => setEditando(null)}
        />
      )}
    </section>
  )
}
