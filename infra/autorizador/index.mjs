// Autorizador del API Gateway: valida el JWT de los DOS emisores de la plataforma.
//
//   - Microsoft Entra ID (login con Microsoft): access token, aud = client id de la API.
//   - Amazon Cognito (login con Google): ID token, aud = app client.
//
// Un autorizador JWT nativo del API Gateway acepta un solo emisor por ruta; por eso esta función.
// No usa dependencias: fetch y crypto vienen con Node 20. Valida lo mismo que el backend:
// algoritmo, firma con la clave del emisor (JWKS), vigencia, emisor y audiencia.
//
// EMISORES (variable de entorno): [{"iss":"https://...","aud":"..."}, ...]

import { createPublicKey, verify } from 'node:crypto'

const EMISORES = JSON.parse(process.env.EMISORES ?? '[]')
  .filter((e) => e.iss && e.aud)
  .map((e) => ({ iss: e.iss.replace(/\/$/, ''), aud: e.aud }))

const TOLERANCIA_S = 60
const clavesPorEmisor = new Map() // iss -> { kid -> KeyObject }, vive mientras viva el contenedor

/** Decodifica una parte base64url del JWT. */
const parte = (texto) => JSON.parse(Buffer.from(texto, 'base64url').toString('utf8'))

/** Las claves públicas del emisor, descubiertas por OIDC y guardadas en memoria. */
async function claves(iss, refrescar = false) {
  if (!refrescar && clavesPorEmisor.has(iss)) return clavesPorEmisor.get(iss)
  const config = await (await fetch(`${iss}/.well-known/openid-configuration`)).json()
  const { keys } = await (await fetch(config.jwks_uri)).json()
  const mapa = new Map(keys.filter((k) => k.kty === 'RSA').map((k) => [k.kid, createPublicKey({ key: k, format: 'jwk' })]))
  clavesPorEmisor.set(iss, mapa)
  return mapa
}

/** Devuelve el emisor si el token es válido; si no, lanza un error con el motivo. */
export async function validar(token) {
  const [h, p, firma] = token.split('.')
  if (!h || !p || !firma) throw new Error('no es un JWT')
  const cabecera = parte(h)
  const datos = parte(p)
  if (cabecera.alg !== 'RS256') throw new Error('algoritmo no permitido')

  const iss = String(datos.iss ?? '').replace(/\/$/, '')
  const emisor = EMISORES.find((e) => e.iss === iss)
  if (!emisor) throw new Error('emisor no aceptado')

  let mapa = await claves(iss)
  // Si el kid no está, el emisor pudo rotar sus claves: se refresca una vez.
  if (!mapa.has(cabecera.kid)) mapa = await claves(iss, true)
  const clave = mapa.get(cabecera.kid)
  if (!clave) throw new Error('clave desconocida')
  const firmaValida = verify('RSA-SHA256', Buffer.from(`${h}.${p}`), clave, Buffer.from(firma, 'base64url'))
  if (!firmaValida) throw new Error('firma inválida')

  const ahora = Math.floor(Date.now() / 1000)
  if (typeof datos.exp !== 'number' || datos.exp + TOLERANCIA_S < ahora) throw new Error('token vencido')
  if (typeof datos.nbf === 'number' && datos.nbf - TOLERANCIA_S > ahora) throw new Error('token aún no vigente')

  const audiencias = Array.isArray(datos.aud) ? datos.aud : [datos.aud]
  if (!audiencias.includes(emisor.aud) && !audiencias.includes(`api://${emisor.aud}`)) {
    throw new Error('audiencia incorrecta')
  }
  return iss
}

/** Formato simple del autorizador de un HTTP API (payload 2.0). */
export async function handler(evento) {
  const cabecera = evento.headers?.authorization ?? ''
  const token = cabecera.startsWith('Bearer ') ? cabecera.slice(7).trim() : ''
  try {
    const emisor = await validar(token)
    return { isAuthorized: true, context: { emisor } }
  } catch (error) {
    // El motivo queda en CloudWatch, nunca en la respuesta: no se le dan pistas a quien prueba.
    console.log(JSON.stringify({ rechazado: error.message, ruta: evento.rawPath }))
    return { isAuthorized: false }
  }
}
