// Avisos entre pantallas que no se conocen: cuando la cabecera responde una solicitud, el perfil
// tiene que recargar sus bandejas. Un evento del navegador evita pasar funciones por todo el árbol.
const PREFIJO = 'duocconecta:'

/** Avisa que algo cambió: 'colaboraciones', 'vitrina'… */
export function avisarCambio(que) {
  window.dispatchEvent(new Event(PREFIJO + que))
}

/** Escucha un cambio. Devuelve la función que deja de escuchar, lista para un useEffect. */
export function alCambiar(que, accion) {
  window.addEventListener(PREFIJO + que, accion)
  return () => window.removeEventListener(PREFIJO + que, accion)
}
