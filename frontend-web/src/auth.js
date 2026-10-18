// Autenticación con dos proveedores. Ningún otro archivo habla con MSAL ni con oidc-client-ts.
//
//   - Microsoft (cuenta Duoc): MSAL contra Azure AD / Entra ID. Se manda el ACCESS token de la API.
//   - Google: oidc-client-ts contra Amazon Cognito, que federa a Google. Se manda el ID token, que
//     es el que trae 'email' y 'aud' (Cognito no pone 'aud' en el access token).
//
// Los dos usan Authorization Code + PKCE y ninguno tiene client secret: un SPA no puede guardarlo.
// El resto de la aplicación solo ve esta interfaz y no sabe con qué proveedor entró cada persona.

import { PublicClientApplication, InteractionRequiredAuthError, EventType } from '@azure/msal-browser'
import { UserManager, WebStorageStateStore } from 'oidc-client-ts'

// sessionStorage y no localStorage: la sesión se borra al cerrar la pestaña.
// Es lo que protege la cuenta en los computadores compartidos del taller (HU-03).
const CLAVE_PROVEEDOR = 'duocconecta.proveedor'
const proveedorElegido = () => window.sessionStorage.getItem(CLAVE_PROVEEDOR)

// --- Microsoft (Entra ID) ---------------------------------------------------

export const configuracionMsal = {
  auth: {
    clientId: import.meta.env.VITE_AZURE_CLIENT_ID,
    authority: `https://login.microsoftonline.com/${import.meta.env.VITE_AZURE_TENANT_ID}`,
    redirectUri: window.location.origin,
  },
  cache: {
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: false,
  },
}

// El scope de la API, no los de OIDC. Pedir solo "openid profile" devuelve un ID token
// cuya audiencia es el SPA, y el ValidadorAudiencia del backend lo rechaza con 401.
export const solicitudDeToken = { scopes: [import.meta.env.VITE_AZURE_SCOPE] }

export const msal = new PublicClientApplication(configuracionMsal)

// --- Google (Cognito) ---------------------------------------------------------

const cognito = {
  autoridad: import.meta.env.VITE_COGNITO_AUTORIDAD,
  clientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
  dominio: import.meta.env.VITE_COGNITO_DOMINIO,
}

/** Si el build no trae los datos de Cognito, el login con Google no está disponible. */
export const googleDisponible = Boolean(cognito.autoridad && cognito.clientId && cognito.dominio)

const gestorGoogle = googleDisponible
  ? new UserManager({
      authority: cognito.autoridad,
      client_id: cognito.clientId,
      redirect_uri: window.location.origin,
      response_type: 'code',
      scope: 'openid email profile',
      userStore: new WebStorageStateStore({ store: window.sessionStorage }),
      // Renueva los tokens antes de que venzan, con el refresh token y sin salir de la página.
      automaticSilentRenew: true,
    })
  : null

let usuarioGoogle = null

// --- Interfaz común -----------------------------------------------------------

/** Prepara la sesión. Hay que llamarlo una vez antes de pintar la aplicación. */
export async function inicializar() {
  await msal.initialize()
  if (proveedorElegido() === 'google' && gestorGoogle) {
    const parametros = new URLSearchParams(window.location.search)
    // Vuelta del login: Cognito redirige con ?code=...&state=... y aquí se canjea por los tokens.
    if (parametros.has('code') && parametros.has('state')) {
      try {
        usuarioGoogle = await gestorGoogle.signinRedirectCallback()
      } finally {
        // El código es de un solo uso: se saca de la URL para que recargar no lo reintente.
        window.history.replaceState({}, document.title, window.location.pathname)
      }
    } else {
      usuarioGoogle = await gestorGoogle.getUser()
    }
    if (usuarioGoogle?.expired) usuarioGoogle = null
  } else {
    await msal.handleRedirectPromise()
  }
}

/** Devuelve la cuenta que inició sesión ({ username }), o null si todavía no entró nadie. */
export function cuentaActual() {
  if (proveedorElegido() === 'google') {
    return usuarioGoogle ? { username: usuarioGoogle.profile.email ?? usuarioGoogle.profile.sub } : null
  }
  return msal.getAllAccounts()[0] ?? null
}

/**
 * Manda a la pantalla de inicio de sesión del proveedor elegido: 'microsoft' (por defecto) o
 * 'google'. Con Google se salta directo a Google, sin pasar por la página de Cognito. Si la
 * persona escribió su correo, va como `login_hint` y el proveedor lo deja escrito.
 */
export function iniciarSesion(proveedor = proveedorElegido() ?? 'microsoft', correo = '') {
  if (proveedor === 'google') {
    if (!gestorGoogle) throw new Error('El inicio de sesión con Google no está configurado.')
    window.sessionStorage.setItem(CLAVE_PROVEEDOR, 'google')
    const extraQueryParams = { identity_provider: 'Google', ...(correo ? { login_hint: correo } : {}) }
    return gestorGoogle.signinRedirect({ extraQueryParams })
  }
  window.sessionStorage.setItem(CLAVE_PROVEEDOR, 'microsoft')
  return msal.loginRedirect({ ...solicitudDeToken, ...(correo ? { loginHint: correo } : {}) })
}

/** Cierra la sesión en el navegador y también en el proveedor, para que no vuelva a entrar solo. */
export async function cerrarSesion() {
  const proveedor = proveedorElegido()
  window.sessionStorage.removeItem(CLAVE_PROVEEDOR)
  if (proveedor === 'google' && gestorGoogle) {
    await gestorGoogle.removeUser()
    usuarioGoogle = null
    const salida = new URL(`https://${cognito.dominio}/logout`)
    salida.searchParams.set('client_id', cognito.clientId)
    salida.searchParams.set('logout_uri', window.location.origin)
    window.location.assign(salida.toString())
    return
  }
  return msal.logoutRedirect({ account: msal.getAllAccounts()[0] ?? null })
}

/**
 * Un token válido para la API: el access token de Microsoft o el ID token de Cognito. Primero en
 * silencio (caché o renovación); solo si el proveedor pide intervención humana, manda al login.
 */
export async function obtenerToken() {
  if (proveedorElegido() === 'google') {
    usuarioGoogle = await gestorGoogle.getUser()
    if (usuarioGoogle && !usuarioGoogle.expired) return usuarioGoogle.id_token
    try {
      usuarioGoogle = await gestorGoogle.signinSilent()
      if (usuarioGoogle) return usuarioGoogle.id_token
    } catch {
      // El refresh token también venció: hay que volver a iniciar sesión.
    }
    return iniciarSesion('google')
  }

  const cuenta = msal.getAllAccounts()[0]
  if (!cuenta) return iniciarSesion('microsoft')
  try {
    const respuesta = await msal.acquireTokenSilent({ ...solicitudDeToken, account: cuenta })
    return respuesta.accessToken
  } catch (error) {
    if (error instanceof InteractionRequiredAuthError) {
      return msal.acquireTokenRedirect(solicitudDeToken)
    }
    throw error
  }
}

/**
 * El contenido del token, decodificado. Un JWT son tres partes separadas por punto y la del medio
 * son los datos. Aquí solo se leen para mostrar: la firma la verifica el backend, nunca el navegador.
 */
export async function claimsDelToken() {
  const token = await obtenerToken()
  const cuerpo = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
  return JSON.parse(decodeURIComponent(escape(atob(cuerpo))))
}

/**
 * Avisa cuando la sesión cambia, con cualquiera de los dos proveedores. Sin esto la pantalla se
 * queda como estaba aunque la sesión ya no sirva. Devuelve una función para dejar de escuchar.
 */
export function alCambiarSesion(callback) {
  const id = msal.addEventCallback((evento) => {
    const relevantes = [
      EventType.LOGIN_SUCCESS,
      EventType.LOGOUT_SUCCESS,
      EventType.ACQUIRE_TOKEN_SUCCESS,
      EventType.ACQUIRE_TOKEN_FAILURE,
    ]
    if (relevantes.includes(evento.eventType) && proveedorElegido() !== 'google') callback(cuentaActual())
  })

  const alCargar = (u) => { usuarioGoogle = u; callback(cuentaActual()) }
  const alSalir = () => { usuarioGoogle = null; callback(null) }
  gestorGoogle?.events.addUserLoaded(alCargar)
  gestorGoogle?.events.addUserUnloaded(alSalir)
  gestorGoogle?.events.addSilentRenewError(alSalir)

  return () => {
    if (id) msal.removeEventCallback(id)
    gestorGoogle?.events.removeUserLoaded(alCargar)
    gestorGoogle?.events.removeUserUnloaded(alSalir)
    gestorGoogle?.events.removeSilentRenewError(alSalir)
  }
}
