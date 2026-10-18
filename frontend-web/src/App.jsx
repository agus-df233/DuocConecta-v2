// Estructura de la aplicación: pantalla de login si no hay sesión, o la aplicación con su
// cabecera y sus vistas (Vitrina, Perfil y, para ADMINISTRADOR, Administración) si la persona ya entró.
import { useCallback, useEffect, useRef, useState } from 'react'
import { cuentaActual, iniciarSesion, cerrarSesion, claimsDelToken, alCambiarSesion, googleDisponible } from './auth'
import Accesibilidad from './Accesibilidad'
import AceptarSolicitud from './AceptarSolicitud'
import Administracion from './Administracion'
import Aviso, { useAviso } from './Aviso'
import { AvisosEmergentes, CentroNotificaciones, DURACIONES_PUSH, useNotificaciones } from './CentroNotificaciones'
import { obtenerMiPerfil, responderColaboracion } from './api'
import { avisarCambio } from './eventos'
import insigniaDuoc from './assets/duoc-uc.jpg'
import { iniciales, tipoDeAviso } from './catalogo'
import { CLAVE_DESTINO, PREFIJO_ENLACE } from './enlaces'
import Perfil from './Perfil'
import { usePreferencias } from './preferencias'
import Vitrina from './Vitrina'

/**
 * Un enlace compartido abre un proyecto: https://…/#proyecto-<id>. Si la persona todavía no
 * había entrado, el destino quedó guardado antes del login, porque el proveedor no conserva el #.
 */
function destinoInicial() {
  let guardado = null
  try {
    guardado = window.sessionStorage.getItem(CLAVE_DESTINO)
    window.sessionStorage.removeItem(CLAVE_DESTINO)
  } catch { /* sin almacenamiento no hay destino guardado */ }
  const enlace = window.location.hash.startsWith(PREFIJO_ENLACE) ? window.location.hash : guardado
  return enlace?.startsWith(PREFIJO_ENLACE) ? { proyectoId: enlace.slice(PREFIJO_ENLACE.length) } : null
}

export default function App() {
  // La cuenta se guarda en estado y no se lee en cada render: así, cuando la sesión se pierde
  // o se cierra, la aplicación vuelve sola al login en vez de quedar mostrando vistas vacías.
  const [cuenta, setCuenta] = useState(cuentaActual())
  const [preferencias, cambiarPreferencias] = usePreferencias()
  const [esAdministrador, setEsAdministrador] = useState(false)

  // Dónde está la persona, y qué hay que abrir al llegar: un proyecto o una sección del perfil.
  const [vista, setVista] = useState('vitrina')
  const [foco, setFoco] = useState(destinoInicial)

  useEffect(() => alCambiarSesion(setCuenta), [])

  // Las preferencias de accesibilidad van en <html>: así alcanzan también al login y a las ventanas.
  useEffect(() => {
    const raiz = document.documentElement.dataset
    raiz.texto = String(preferencias.texto)
    raiz.contraste = preferencias.contraste ? 'alto' : 'normal'
    raiz.movimiento = preferencias.movimiento ? 'reducido' : 'normal'
  }, [preferencias.texto, preferencias.contraste, preferencias.movimiento])

  // Los roles salen del BFF y no del token: la lista de administradores solo existe en el backend.
  // Ocultar el botón es comodidad; quien protege de verdad es el 403 de cada ruta /admin.
  useEffect(() => {
    if (!cuenta) return
    obtenerMiPerfil()
      .then((p) => setEsAdministrador((p.roles ?? []).includes('ADMINISTRADOR')))
      .catch(() => setEsAdministrador(false))
  }, [cuenta])

  /** Lleva a una vista y, si corresponde, a lo que hay que abrir en ella. */
  const irA = useCallback((nuevaVista, nuevoFoco = null) => {
    setVista(nuevaVista)
    setFoco(nuevoFoco)
    window.scrollTo({ top: 0 })
  }, [])

  /** Adónde lleva un aviso: el proyecto que lo originó o la sección del perfil que corresponde. */
  const irAlAviso = useCallback((aviso) => {
    const destino = aviso.destino ?? tipoDeAviso(aviso.tipo).destino
    if (destino === 'proyecto') irA('vitrina', aviso.proyectoId ? { proyectoId: aviso.proyectoId } : null)
    else irA('perfil', { seccion: destino })
  }, [irA])

  const usarFoco = useCallback(() => setFoco(null), [])

  if (!cuenta) return <Login />

  return (
    <div className="app">
      <a className="saltar" href="#contenido">Saltar al contenido</a>
      <Cabecera
        vista={vista}
        irA={irA}
        irAlAviso={irAlAviso}
        correo={cuenta.username}
        esAdministrador={esAdministrador}
        preferencias={preferencias}
        cambiarPreferencias={cambiarPreferencias}
      />
      <main id="contenido" tabIndex={-1}>
        {vista === 'vitrina' && (
          <Vitrina
            foco={foco}
            alUsarFoco={usarFoco}
            escondidos={preferencias.escondidos}
            alEsconder={(escondidos) => cambiarPreferencias({ escondidos })}
            guardada={preferencias.vitrina}
            alGuardar={(vitrina) => cambiarPreferencias({ vitrina })}
          />
        )}
        {vista === 'perfil' && (
          <Perfil
            foco={foco}
            alUsarFoco={usarFoco}
            preferencias={preferencias}
            cambiarPreferencias={cambiarPreferencias}
          />
        )}
        {vista === 'admin' && esAdministrador && <Administracion />}
      </main>
    </div>
  )
}

/**
 * Cabecera fija: navegación, notificaciones, accesibilidad y cuenta. Las solicitudes pendientes
 * viven en el centro de notificaciones porque son lo único que le pide algo a la persona.
 */
function Cabecera({ vista, irA, irAlAviso, correo, esAdministrador, preferencias, cambiarPreferencias }) {
  const [abierto, setAbierto] = useState(null)   // 'notificaciones' | 'accesibilidad' | 'cuenta' | null
  const [aceptando, setAceptando] = useState(null)
  const [nombre, setNombre] = useState('')
  const { texto: aviso, avisar } = useAviso()
  const controles = useRef(null)
  const notificaciones = useNotificaciones({ push: preferencias.push })

  // El nombre sale del token y no del perfil: sirve para el avatar antes de cargar nada más.
  useEffect(() => {
    claimsDelToken().then((c) => setNombre(c.name ?? correo)).catch(() => setNombre(correo))
  }, [correo])

  // Un panel abierto se cierra con Esc o con un clic fuera de él, como cualquier menú.
  useEffect(() => {
    if (!abierto) return undefined
    const alClic = (e) => { if (!controles.current?.contains(e.target)) setAbierto(null) }
    const alTeclear = (e) => { if (e.key === 'Escape') setAbierto(null) }
    document.addEventListener('mousedown', alClic)
    document.addEventListener('keydown', alTeclear)
    return () => {
      document.removeEventListener('mousedown', alClic)
      document.removeEventListener('keydown', alTeclear)
    }
  }, [abierto])

  const alternar = (cual) => setAbierto(abierto === cual ? null : cual)

  // Alt+N abre y cierra el centro desde cualquier parte. Se mira e.code y no e.key: en macOS,
  // Alt+N escribe "˜" en vez de "n".
  useEffect(() => {
    const alTeclear = (e) => {
      if (e.altKey && !e.ctrlKey && !e.metaKey && e.code === 'KeyN') {
        e.preventDefault()
        setAbierto((actual) => (actual === 'notificaciones' ? null : 'notificaciones'))
      }
    }
    document.addEventListener('keydown', alTeclear)
    return () => document.removeEventListener('keydown', alTeclear)
  }, [])
  const ir = (nuevaVista) => { setAbierto(null); irA(nuevaVista) }

  // Aceptar abre la ventana de consentimiento; rechazar no comparte nada y va directo.
  const responder = async (solicitud, aceptar, compartirTelefono = false) => {
    try {
      await responderColaboracion(solicitud.id, aceptar, compartirTelefono)
      avisar(aceptar
        ? 'Aceptaste la solicitud: ya pueden ver sus datos de contacto.'
        : 'Rechazaste la solicitud. No se compartió ningún dato.')
      setAceptando(null)
      notificaciones.recargarPendientes()
      avisarCambio('colaboraciones')
    } catch (e) {
      avisar(e.message)
    }
  }

  const botonVista = (id, texto) => (
    <button
      type="button"
      className={vista === id ? 'activo' : ''}
      aria-current={vista === id ? 'page' : undefined}
      onClick={() => ir(id)}
    >
      {texto}
    </button>
  )

  return (
    <header>
      <div className="barra">
        <div className="marca">
          <span className="logo" aria-hidden="true" />
          <strong>DuocConecta</strong>
        </div>

        <nav aria-label="Principal">
          {botonVista('vitrina', 'Vitrina')}
          {botonVista('perfil', 'Perfil')}
          {esAdministrador && botonVista('admin', 'Administración')}
          {/* La biblioteca de prompts es la siguiente entrega. Se deja a la vista, apagada,
              para que se entienda que está planificada y no olvidada. */}
          <span className="pronto" title="Biblioteca de prompts: llega en la próxima entrega">
            Prompts<span>Pronto</span>
          </span>
        </nav>

        {/* Los controles de cuenta van juntos: si la cabecera envuelve, se mueven como una sola
            pieza al borde derecho en vez de separarse. */}
        <div className="controles-cuenta" ref={controles}>
          <CentroNotificaciones
            notificaciones={notificaciones}
            abierto={abierto === 'notificaciones'}
            alAlternar={() => alternar('notificaciones')}
            alIr={(a) => { setAbierto(null); irAlAviso(a) }}
            alAceptar={(s) => { setAbierto(null); setAceptando(s) }}
            alRechazar={(s) => responder(s, false)}
          />
          <Accesibilidad
            preferencias={preferencias}
            alCambiar={cambiarPreferencias}
            abierto={abierto === 'accesibilidad'}
            alAlternar={() => alternar('accesibilidad')}
          />
          <div className="desplegable">
            <button
              type="button"
              className="avatar"
              onClick={() => alternar('cuenta')}
              aria-label="Menú de cuenta"
              aria-expanded={abierto === 'cuenta'}
            >
              {iniciales(nombre)}
            </button>
            {abierto === 'cuenta' && (
              <div className="panel-flotante angosto">
                <p className="ayuda correo-cuenta">{correo}</p>
                <button type="button" className="opcion" onClick={() => ir('perfil')}>Ver mi perfil</button>
                <button type="button" className="opcion salir" onClick={cerrarSesion}>Cerrar sesión</button>
              </div>
            )}
          </div>
        </div>
      </div>

      <AvisosEmergentes
        emergentes={notificaciones.emergentes}
        duracion={(DURACIONES_PUSH[preferencias.duracionAvisos] ?? DURACIONES_PUSH[0]).ms}
        alCerrar={notificaciones.cerrarEmergente}
        alIr={(a) => { notificaciones.marcar(a); irAlAviso(a) }}
        alVerTodos={() => { notificaciones.cerrarEmergentes(); setAbierto('notificaciones') }}
      />

      {aceptando && (
        <AceptarSolicitud
          solicitud={aceptando}
          alConfirmar={(compartirTelefono) => responder(aceptando, true, compartirTelefono)}
          alCerrar={() => setAceptando(null)}
        />
      )}

      <Aviso texto={aviso} />
    </header>
  )
}

// Dominios que entran con Microsoft. Es solo una ayuda para quien escribe mal el correo: quien
// decide de verdad es el backend, con ResolvedorRol.
const DOMINIOS_DUOC = ['duocuc.cl', 'profesor.duoc.cl', 'duoc.cl']
const esInstitucional = (correo) => DOMINIOS_DUOC.includes(correo.split('@').pop().toLowerCase())

/** Pantalla de entrada. Microsoft (Entra ID) o Google (vía Cognito), los dos con Authorization Code + PKCE. */
function Login() {
  const [correo, setCorreo] = useState('')
  const [error, setError] = useState(null)       // 'dominio' | texto de un error de configuración
  const [conectando, setConectando] = useState(null)

  const entrar = (proveedor) => {
    const escrito = correo.trim()
    if (proveedor === 'microsoft' && escrito && !esInstitucional(escrito)) {
      setError('dominio')
      return
    }
    if (proveedor === 'google' && !googleDisponible) {
      setError('El inicio de sesión con Google todavía no está habilitado. Entra con tu cuenta Duoc.')
      return
    }
    // El # de un enlace compartido no sobrevive al viaje al proveedor: se guarda para la vuelta.
    try {
      if (window.location.hash.startsWith(PREFIJO_ENLACE)) {
        window.sessionStorage.setItem(CLAVE_DESTINO, window.location.hash)
      }
    } catch { /* sin almacenamiento se pierde el destino, no el login */ }
    setError(null)
    setConectando(proveedor)
    Promise.resolve(iniciarSesion(proveedor, escrito)).catch((e) => {
      setConectando(null)
      setError(e.message)
    })
  }

  return (
    <main className="login">
      <section className="login-panel" aria-label="Presentación">
        <span className="circulo grande" aria-hidden="true" />
        <span className="circulo" aria-hidden="true" />
        <div className="login-marca">
          <span className="logo" aria-hidden="true" />
          <span>DuocConecta</span>
        </div>
        <div className="login-titular">
          <h1>Comparte tus proyectos con la comunidad y colabora con otros</h1>
          <p>
            Publica lo que estás haciendo o desarrollando y encuentra gente de otras carreras y
            sedes para colaborar activamente.
          </p>
        </div>
        {/* Una muestra de lo que pasa dentro: un proyecto que busca equipo y una solicitud aceptada. */}
        <div className="login-muestra" aria-hidden="true">
          <div className="muestra-tarjeta">
            <div className="muestra-fila">
              <span className="insignia BUSCANDO_EQUIPO">Busca equipo</span>
              <span className="muestra-sede">Valparaíso</span>
            </div>
            <p className="muestra-titulo">Caleta App</p>
            <div className="chips">
              {['React Native', 'Node', 'Claude', 'Gemini'].map((h) => <span key={h} className="chip oscuro">{h}</span>)}
            </div>
          </div>
          <div className="muestra-aviso">
            <span className="punto-verde" />
            <span><strong>Javiera</strong> aceptó tu solicitud</span>
          </div>
        </div>
      </section>

      <section className="login-form" aria-labelledby="titulo-login">
        <div className="login-caja">
          <img className="insignia-duoc" src={insigniaDuoc} alt="Duoc UC" />
          <h2 id="titulo-login">Entra con tu cuenta Duoc</h2>
          <p className="ayuda login-explica">
            Solo se permiten cuentas <strong>@duocuc.cl</strong>, <strong>@profesor.duoc.cl</strong> y{' '}
            <strong>@duoc.cl</strong>. Usamos el login institucional, así que no necesitas otra contraseña.
          </p>

          <label className="etiqueta-login" htmlFor="correo-login">
            Correo institucional <span className="opcional">(opcional)</span>
          </label>
          <input
            id="correo-login"
            type="email"
            autoComplete="email"
            inputMode="email"
            placeholder="nombre.apellido@duocuc.cl"
            value={correo}
            onChange={(e) => { setCorreo(e.target.value); setError(null) }}
            onKeyDown={(e) => { if (e.key === 'Enter') entrar('microsoft') }}
            aria-invalid={error === 'dominio'}
            aria-describedby="correo-login-ayuda"
            className={error === 'dominio' ? 'campo-login malo' : 'campo-login'}
          />
          <p id="correo-login-ayuda" className="ayuda">Si lo escribes, lo validamos antes de redirigirte.</p>

          {error && (
            <div className="alerta-login" role="alert">
              <span className="alerta-icono" aria-hidden="true">!</span>
              {error === 'dominio' ? (
                <div>
                  <p className="alerta-titulo">Ese correo no es institucional</p>
                  <p>
                    Usa tu cuenta de Duoc UC. Si no la recuerdas, revísala en el portal de alumnos o
                    en Punto Estudiantil. Las cuentas de Google entran con su propio botón, solo si
                    el equipo las autorizó.
                  </p>
                </div>
              ) : (
                <div><p>{error}</p></div>
              )}
            </div>
          )}

          <div className="botones-login">
            <button
              type="button"
              className="boton-login microsoft"
              onClick={() => entrar('microsoft')}
              disabled={Boolean(conectando)}
              aria-busy={conectando === 'microsoft'}
            >
              <span className="logo-microsoft" aria-hidden="true"><span /><span /><span /><span /></span>
              <span className="crece">{conectando === 'microsoft' ? 'Conectando con Microsoft…' : 'Entrar con Microsoft'}</span>
              <span className="recomendado">Recomendado</span>
            </button>
            <button
              type="button"
              className="boton-login google"
              onClick={() => entrar('google')}
              disabled={Boolean(conectando)}
              aria-busy={conectando === 'google'}
            >
              <span className="logo-google" aria-hidden="true" />
              <span className="crece">{conectando === 'google' ? 'Conectando con Google…' : 'Entrar con Google'}</span>
            </button>
          </div>

          <div className="nota-privacidad">
            <span className="candado" aria-hidden="true" />
            <p>Tu teléfono y tus redes no son públicos. Solo se comparten cuando aceptas una solicitud de colaboración.</p>
          </div>
        </div>
      </section>
    </main>
  )
}
