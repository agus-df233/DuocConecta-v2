// Ventanas modales con lo que pide la accesibilidad: Esc cierra, el foco entra a la ventana, Tab
// no se escapa de ella y, al cerrar, el foco vuelve al botón que la abrió.
import { useEffect, useId, useRef } from 'react'

const ENFOCABLES = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), '
  + 'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

// Cuántas ventanas hay abiertas: el fondo se bloquea con la primera y se libera con la última.
let abiertas = 0

/**
 * Esc, foco atrapado y foco devuelto, para cualquier caja: una ventana o el panel lateral.
 * Con `enfocarCampo`, el foco va al primer campo; si no, a la caja misma, que no hace saltar el scroll.
 */
export function useAtraparFoco(alCerrar, { enfocarCampo = false } = {}) {
  const caja = useRef(null)
  const cerrar = useRef(alCerrar)
  cerrar.current = alCerrar

  useEffect(() => {
    const anterior = document.activeElement
    const elemento = caja.current
    const campo = enfocarCampo ? elemento?.querySelector('input:not([disabled]), textarea, select') : null
    ;(campo ?? elemento)?.focus()

    const alTeclear = (evento) => {
      if (evento.key === 'Escape') {
        evento.stopPropagation()
        cerrar.current()
        return
      }
      if (evento.key !== 'Tab') return
      const lista = [...elemento.querySelectorAll(ENFOCABLES)]
      if (lista.length === 0) return
      const primero = lista[0]
      const ultimo = lista[lista.length - 1]
      if (evento.shiftKey && document.activeElement === primero) {
        evento.preventDefault()
        ultimo.focus()
      } else if (!evento.shiftKey && document.activeElement === ultimo) {
        evento.preventDefault()
        primero.focus()
      }
    }

    elemento?.addEventListener('keydown', alTeclear)
    abiertas += 1
    document.body.classList.add('sin-scroll')
    return () => {
      elemento?.removeEventListener('keydown', alTeclear)
      abiertas -= 1
      if (abiertas === 0) document.body.classList.remove('sin-scroll')
      anterior?.focus?.()
    }
  }, [enfocarCampo])

  return caja
}

/** Ventana centrada con su título y el botón de cerrar. El contenido va en `children`. */
export default function Ventana({ titulo, alCerrar, children, ancha = false, enfocarCampo = true }) {
  const id = useId()
  const caja = useAtraparFoco(alCerrar, { enfocarCampo })

  return (
    <>
      <div className="modal-fondo" onClick={alCerrar} />
      <div className="modal-caja">
        <div
          ref={caja}
          className={ancha ? 'modal ancha' : 'modal'}
          role="dialog"
          aria-modal="true"
          aria-labelledby={id}
          tabIndex={-1}
        >
          <div className="modal-cabecera">
            <h2 id={id}>{titulo}</h2>
            <button type="button" className="cerrar" onClick={alCerrar} aria-label="Cerrar">×</button>
          </div>
          {children}
        </div>
      </div>
    </>
  )
}
