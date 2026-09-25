// Una tarjeta de la vitrina: lo justo para decidir si vale la pena abrirla.
// El detalle completo va en el panel lateral.
import { ESTADOS, iniciales, fechaCorta, TEXTO_SOLICITUD } from './catalogo'

export default function TarjetaProyecto({ proyecto, esMio, alAbrir, alPedirContacto, estadoSolicitud }) {
  const oculto = proyecto.visibilidad !== 'PUBLICO'
  const autor = proyecto.autor ?? {}

  // Toda la tarjeta abre el detalle, así que las acciones tienen que frenar el clic
  // para que no dispare las dos cosas a la vez.
  const soloEsto = (accion) => (evento) => {
    evento.stopPropagation()
    accion()
  }

  return (
    <article
      className="tarjeta proyecto"
      tabIndex={0}
      onClick={alAbrir}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); alAbrir() } }}
    >
      <div className="etiquetas">
        <span className={`insignia ${proyecto.estado}`}>{ESTADOS[proyecto.estado] ?? proyecto.estado}</span>
        {esMio && <span className="insignia propio">Tuyo</span>}
        {esMio && oculto && <span className="insignia oculto">Fuera de la vitrina</span>}
        <span className="fecha">{fechaCorta(proyecto.fechaCreacion)}</span>
      </div>

      <h3>{proyecto.nombre}</h3>
      <p>{proyecto.resumen}</p>

      {proyecto.herramientas?.length > 0 && (
        <div className="chips">
          {proyecto.herramientas.map((h) => <span key={h} className="chip">{h}</span>)}
        </div>
      )}

      <div className="pie-autor">
        <div className="iniciales">{iniciales(autor.nombre)}</div>
        <div className="quien">
          <strong>{autor.nombre ?? 'Alguien de la comunidad'}</strong>
          <span>{[autor.carrera, proyecto.sede ?? autor.sede].filter(Boolean).join(' · ')}</span>
        </div>
        <span className="comentarios">{proyecto.cantidadComentarios} com.</span>
      </div>

      <div className="acciones">
        {esMio ? (
          <button className="secundario crece" onClick={soloEsto(alAbrir)}>Ver detalle</button>
        ) : (
          <button
            className="principal crece"
            disabled={Boolean(estadoSolicitud)}
            onClick={soloEsto(alPedirContacto)}
          >
            {TEXTO_SOLICITUD[estadoSolicitud] ?? 'Pedir contacto'}
          </button>
        )}
      </div>
    </article>
  )
}
