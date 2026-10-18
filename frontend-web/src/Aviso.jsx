// Mensaje flotante de confirmación.
// Para las acciones que no cambian de pantalla: publicar, aceptar, ocultar. Puede traer una acción,
// como "Deshacer", y entonces dura más para que alcance a usarse.
import { useCallback, useEffect, useState } from 'react'

/** El mensaje se va solo: es una confirmación, no algo que haya que cerrar. */
export function useAviso(segundos = 4) {
  const [aviso, setAviso] = useState({ texto: '', accion: null })

  useEffect(() => {
    if (!aviso.texto) return undefined
    const duracion = aviso.accion ? Math.max(segundos, 8) : segundos
    const reloj = setTimeout(() => setAviso({ texto: '', accion: null }), duracion * 1000)
    return () => clearTimeout(reloj)
  }, [aviso, segundos])

  /** `accion`, opcional: { texto, alTocar }. Tocarla también cierra el mensaje. */
  const avisar = useCallback((texto, accion = null) => setAviso({ texto, accion }), [])
  const cerrar = useCallback(() => setAviso({ texto: '', accion: null }), [])

  return { texto: aviso.texto, accion: aviso.accion, avisar, cerrar }
}

/** El mensaje en pantalla. role="status": el lector de pantalla lo lee sin mover el foco. */
export default function Aviso({ texto, accion, alCerrar }) {
  return (
    <div className="region-mensaje" role="status" aria-live="polite">
      {texto && (
        <div className="mensaje">
          {texto}
          {accion && (
            <button type="button" className="accion-mensaje" onClick={() => { accion.alTocar(); alCerrar?.() }}>
              {accion.texto}
            </button>
          )}
        </div>
      )}
    </div>
  )
}
