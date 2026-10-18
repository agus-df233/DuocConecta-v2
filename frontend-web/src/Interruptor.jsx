// Interruptor de encendido y apagado. Es un role="switch": el lector de pantalla dice si está
// activo, cosa que un botón que solo cambia de color no comunica.
export default function Interruptor({ activo, alCambiar, texto, descripcion, deshabilitado = false, tarjeta = false }) {
  const clases = ['interruptor', activo && 'encendido', tarjeta && 'tarjeta-interruptor'].filter(Boolean).join(' ')
  return (
    <button
      type="button"
      role="switch"
      aria-checked={activo}
      aria-disabled={deshabilitado}
      className={clases}
      onClick={() => { if (!deshabilitado) alCambiar(!activo) }}
    >
      <span className="interruptor-texto">
        <span className="titulo">{texto}</span>
        {descripcion && <span className="descripcion">{descripcion}</span>}
      </span>
      <span className={deshabilitado ? 'riel fijo' : 'riel'} aria-hidden="true"><span className="perilla" /></span>
    </button>
  )
}
