// Una tarjeta de la vitrina: lo justo para decidir si vale la pena abrirla.
// El detalle completo va en el panel lateral.
import { ESTADOS, iniciales, fechaRelativa, TEXTO_SOLICITUD } from './catalogo'

export default function TarjetaProyecto({
  proyecto, esMio, esNuevo, estadoSolicitud, alAbrir, alColaborar, alComentar, alEditar, alEsconder,
}) {
  const oculto = proyecto.visibilidad !== 'PUBLICO'
  const autor = proyecto.autor ?? {}
  const comentarios = proyecto.cantidadComentarios ?? 0

  // Toda la tarjeta abre el detalle, así que las acciones tienen que frenar el clic
  // para que no dispare las dos cosas a la vez.
  const soloEsto = (accion) => (evento) => {
    evento.stopPropagation()
    accion()
  }

  return (
    <article
      className={esNuevo ? 'tarjeta proyecto nuevo' : 'tarjeta proyecto'}
      tabIndex={0}
      aria-label={proyecto.nombre}
      onClick={alAbrir}
      onKeyDown={(e) => {
        if (e.target !== e.currentTarget) return
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); alAbrir() }
      }}
    >
      <div className="etiquetas">
        <span className={`insignia ${proyecto.estado}`}>{ESTADOS[proyecto.estado] ?? proyecto.estado}</span>
        {esNuevo && <span className="insignia-nuevo">Nuevo</span>}
        {esMio && <span className="insignia propio">Tuyo</span>}
        {esMio && oculto && <span className="insignia oculto">Fuera de la vitrina</span>}
        <span className="fecha">{fechaRelativa(proyecto.fechaCreacion)}</span>
      </div>

      <h3>{proyecto.nombre}</h3>
      <p>{proyecto.resumen}</p>

      {proyecto.herramientas?.length > 0 && (
        <div className="chips">
          {proyecto.herramientas.map((h) => <span key={h} className="chip">{h}</span>)}
        </div>
      )}

      <div className="pie-autor">
        <div className="iniciales" aria-hidden="true">{iniciales(autor.nombre)}</div>
        <div className="quien">
          <strong>{autor.nombre ?? 'Alguien de la comunidad'}</strong>
          <span>{[autor.carrera, proyecto.sede ?? autor.sede].filter(Boolean).join(' · ')}</span>
        </div>
        <span className="comentarios" aria-label={`${comentarios} comentarios`}>{comentarios} com.</span>
      </div>

      <div className="acciones">
        {esMio ? (
          <button type="button" className="secundario crece" onClick={soloEsto(alEditar)}>Editar</button>
        ) : (
          <button
            type="button"
            className="principal crece"
            disabled={Boolean(estadoSolicitud)}
            onClick={soloEsto(alColaborar)}
          >
            {TEXTO_SOLICITUD[estadoSolicitud] ?? 'Colaborar'}
          </button>
        )}
        <button type="button" className="secundario crece" onClick={soloEsto(alComentar)}>Comentar</button>
        {!esMio && (
          <button
            type="button"
            className="secundario esconder"
            onClick={soloEsto(alEsconder)}
            title="Esconder de mi vitrina"
            aria-label={`Esconder ${proyecto.nombre} de mi vitrina`}
          >
            –
          </button>
        )}
      </div>
    </article>
  )
}
