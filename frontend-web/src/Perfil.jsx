// Pantalla de perfil: datos propios, solicitudes y proyectos publicados.
// Las solicitudes están aquí y no en una vista aparte porque son información privada.
import { useEffect, useMemo, useState } from 'react'
import {
  obtenerMiPerfil, guardarMiPerfil, alternarVisibilidad,
  listarProyectos, editarProyecto,
} from './api'
import { claimsDelToken } from './auth'
import Aviso, { useAviso } from './Aviso'
import FormularioProyecto from './FormularioProyecto'
import AceptarSolicitud from './AceptarSolicitud'
import { MisColaboraciones, MisSolicitudes, responder, useColaboraciones } from './Solicitudes'
import { ESCUELAS, ESTADOS, SEDES, SIN_CARRERA, iniciales } from './catalogo'

export default function Perfil() {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(null)
  const { texto: aviso, avisar } = useAviso()

  const colaboraciones = useColaboraciones()

  useEffect(() => {
    obtenerMiPerfil().then(setDatos).catch((e) => setError(e.message))
  }, [])

  // Aceptar abre la ventana de consentimiento; rechazar no comparte nada y va directo.
  const [aceptando, setAceptando] = useState(null)

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

  if (error && !datos) return <div className="error">{error}</div>
  if (!datos) return <div className="cargando">Cargando tu perfil…</div>

  const { perfil } = datos

  return (
    <section>
      <div className="cabecera-perfil">
        <div className="marca-grande">{iniciales(perfil.nombre)}</div>
        <div>
          <h1>{perfil.nombre}</h1>
          <p>{[perfil.carrera, perfil.sede].filter(Boolean).join(' · ') || 'Completa tu carrera y sede'}</p>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="columnas">
        <MisDatos datos={datos} alGuardar={setDatos} alAvisar={avisar} alFallar={setError} />

        <section className="tarjeta">
          <h2>Mis solicitudes</h2>
          {colaboraciones.error && <div className="error">{colaboraciones.error}</div>}
          <MisSolicitudes
            recibidas={colaboraciones.recibidas}
            enviadas={colaboraciones.enviadas}
            alResponder={(s, aceptar) =>
              (aceptar ? setAceptando(s) : responderSolicitud(s, false))}
          />

          <h2 style={{ marginTop: 26, marginBottom: 6 }}>Mis colaboraciones</h2>
          <p className="ayuda" style={{ marginBottom: 14 }}>
            Contactos desbloqueados por consentimiento mutuo.
          </p>
          <MisColaboraciones recibidas={colaboraciones.recibidas} enviadas={colaboraciones.enviadas} />
        </section>

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
    <section className="tarjeta">
      <h2>Mis datos</h2>

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
          <h4>Datos que se comparten solo cuando tú aceptas</h4>
          <p>
            Tu teléfono y tus redes no aparecen en la vitrina ni en las búsquedas. Se muestran
            únicamente a las personas cuya solicitud de colaboración aceptas.
          </p>
          <label style={{ marginBottom: 12 }}>
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

      <button
        type="button"
        className={perfil.visible ? 'interruptor' : 'interruptor encendido'}
        onClick={cambiarVisibilidad}
      >
        <span className="riel"><span className="perilla" /></span>
        <span>
          {perfil.visible
            ? 'Perfil visible en las búsquedas. Actívalo para ocultarlo.'
            : 'Tu perfil está oculto: no apareces en las búsquedas ni en los listados de personas.'}
        </span>
      </button>
    </section>
  )
}

/** Los proyectos propios, incluidos los ocultos. Ocultar cambia la visibilidad, no borra. */
function MisProyectos({ alAvisar, alFallar }) {
  const [todos, setTodos] = useState(null)
  const [miOid, setMiOid] = useState(null)
  const [editando, setEditando] = useState(null)

  // El oid del token es lo que dice qué proyectos son propios.
  useEffect(() => {
    claimsDelToken().then((c) => setMiOid(c.oid)).catch(() => setMiOid(null))
  }, [])

  useEffect(() => { recargar() }, [])

  function recargar() {
    listarProyectos().then(setTodos).catch(() => setTodos([]))
  }

  const mios = useMemo(
    () => (todos ?? []).filter((p) => p.propietarioId === miOid),
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
    <section className="tarjeta ancho-completo">
      <h2 style={{ marginBottom: 4 }}>Mis proyectos</h2>
      <p className="ayuda" style={{ marginBottom: 16 }}>
        Ocultar saca el proyecto de la vitrina pública, pero lo conservas aquí.
      </p>

      {todos === null ? (
        <p className="ayuda">Cargando…</p>
      ) : mios.length === 0 ? (
        <p className="ayuda">Aún no publicas nada. Se publica desde la vitrina.</p>
      ) : (
        <div className="grilla">
          {mios.map((p) => (
            <div key={p.id} className="tarjeta">
              <div className="etiquetas">
                <span className={`insignia ${p.estado}`}>{ESTADOS[p.estado] ?? p.estado}</span>
                <span className={p.visibilidad === 'PUBLICO' ? 'insignia propio' : 'insignia oculto'}>
                  {p.visibilidad === 'PUBLICO' ? 'En la vitrina' : 'Fuera de la vitrina'}
                </span>
              </div>
              <h3 style={{ fontSize: 17 }}>{p.nombre}</h3>
              <p className="ayuda">{p.resumen}</p>
              <div className="acciones">
                <button className="secundario" onClick={() => setEditando(p)}>Editar</button>
                <button className="secundario" onClick={() => cambiarVisibilidad(p)}>
                  {p.visibilidad === 'PUBLICO' ? 'Ocultar' : 'Publicar'}
                </button>
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
