// Enlaces directos a un proyecto: https://…/#proyecto-<id>. Con un # y no una ruta, porque el
// frontend se sirve desde S3 por el API Gateway y no hay servidor que reescriba rutas.
export const PREFIJO_ENLACE = '#proyecto-'

// Dónde se guarda el enlace mientras la persona va y vuelve del login.
export const CLAVE_DESTINO = 'duocconecta.destino'

/** El enlace público de un proyecto. Quien lo abre igual tiene que iniciar sesión para verlo. */
export function enlaceDeProyecto(id) {
  return `${window.location.origin}/${PREFIJO_ENLACE}${id}`
}
