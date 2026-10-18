// Preferencias de esta persona en este navegador: tamaño del texto, contraste, movimiento, avisos
// push (y cuánto duran), proyectos escondidos y los filtros de la vitrina. Son comodidades
// locales: si el navegador no deja guardar (ventana privada, almacenamiento bloqueado), la
// aplicación funciona igual con los valores por defecto.
import { useCallback, useState } from 'react'

const CLAVE = 'duocconecta.preferencias'

export const PREFERENCIAS_INICIALES = {
  texto: 0,            // 0 normal · 1 grande · 2 muy grande
  contraste: false,
  movimiento: false,   // true = reducir movimiento
  push: true,
  escondidos: [],      // ids de proyectos que esta persona sacó de su vitrina
  vitrina: null,       // { filtros, orden } de la última visita a la vitrina
  duracionAvisos: 0,   // 0 = 6 s · 1 = 12 s · 2 = hasta cerrarlos
}

function leer() {
  try {
    return { ...PREFERENCIAS_INICIALES, ...JSON.parse(window.localStorage.getItem(CLAVE) ?? '{}') }
  } catch {
    return PREFERENCIAS_INICIALES
  }
}

/** Las preferencias y una función para cambiar algunas. Cada cambio se guarda solo. */
export function usePreferencias() {
  const [preferencias, setPreferencias] = useState(leer)

  const cambiar = useCallback((cambios) => setPreferencias((actuales) => {
    const nuevas = { ...actuales, ...cambios }
    try {
      window.localStorage.setItem(CLAVE, JSON.stringify(nuevas))
    } catch {
      // Sin almacenamiento: el cambio vale solo mientras la pestaña siga abierta.
    }
    return nuevas
  }), [])

  return [preferencias, cambiar]
}
