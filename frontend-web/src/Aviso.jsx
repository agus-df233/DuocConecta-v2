// Mensaje flotante de confirmación.
// Para las acciones que no cambian de pantalla: publicar, aceptar, ocultar.
import { useCallback, useEffect, useState } from 'react'

/** El mensaje se va solo: es una confirmación, no algo que haya que cerrar. */
export function useAviso(segundos = 4) {
  const [texto, setTexto] = useState('')

  useEffect(() => {
    if (!texto) return undefined
    const reloj = setTimeout(() => setTexto(''), segundos * 1000)
    return () => clearTimeout(reloj)
  }, [texto, segundos])

  const avisar = useCallback((mensaje) => setTexto(mensaje), [])

  return { texto, avisar }
}

/** El mensaje en pantalla. No pinta nada si no hay texto. */
export default function Aviso({ texto }) {
  if (!texto) return null
  return <div className="mensaje">{texto}</div>
}
