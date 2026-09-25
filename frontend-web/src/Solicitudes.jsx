// Solicitudes de colaboración y contactos desbloqueados.
// Viven dentro del perfil porque son datos personales, no de la vitrina.
import { useEffect, useState } from 'react'
import { colaboracionesRecibidas, colaboracionesEnviadas, responderColaboracion } from './api'
import { ESTADOS_SOLICITUD, fechaLarga } from './catalogo'
import DatosDeContacto from './DatosDeContacto'

/** Las dos bandejas, compartidas entre solicitudes y colaboraciones para no pedirlas dos veces. */
export function useColaboraciones() {
  const [recibidas, setRecibidas] = useState(null)
  const [enviadas, setEnviadas] = useState(null)
  const [error, setError] = useState(null)

  const recargar = () => {
    setError(null)
    Promise.all([colaboracionesRecibidas(), colaboracionesEnviadas()])
      .then(([r, e]) => { setRecibidas(r); setEnviadas(e) })
      .catch((e) => { setRecibidas([]); setEnviadas([]); setError(e.message) })
  }

  useEffect(recargar, [])

  return { recibidas, enviadas, error, recargar }
}

/** Solo las recibidas y pendientes traen botones: responder es de quien recibe. */
export function MisSolicitudes({ recibidas, enviadas, alResponder }) {
  const [pestana, setPestana] = useState('recibidas')

  const lista = pestana === 'recibidas' ? recibidas : enviadas
  if (lista === null) return <p className="ayuda">Cargando tus solicitudes…</p>

  return (
    <>
      <div className="pestanas">
        <button className={pestana === 'recibidas' ? 'activo' : ''} onClick={() => setPestana('recibidas')}>
          Recibidas
        </button>
        <button className={pestana === 'enviadas' ? 'activo' : ''} onClick={() => setPestana('enviadas')}>
          Enviadas
        </button>
      </div>

      {lista.length === 0 ? (
        <p className="ayuda">
          {pestana === 'recibidas'
            ? 'Aún nadie te ha pedido contacto.'
            : 'Aún no has pedido contacto. Se hace desde la vitrina.'}
        </p>
      ) : (
        <div className="lista">
          {lista.map((s) => (
            <Fila
              key={s.id}
              solicitud={s}
              esRecibida={pestana === 'recibidas'}
              alResponder={alResponder}
            />
          ))}
        </div>
      )}
    </>
  )
}

/** Una solicitud de cualquiera de las dos bandejas. */
function Fila({ solicitud, esRecibida, alResponder }) {
  const otra = esRecibida ? solicitud.solicitante : solicitud.solicitado
  const puedeResponder = esRecibida && solicitud.estado === 'PENDIENTE'

  return (
    <div className="fila-solicitud">
      <div className="etiquetas">
        <span className={`insignia ${solicitud.estado}`}>{ESTADOS_SOLICITUD[solicitud.estado]}</span>
        <span className="fecha">{fechaLarga(solicitud.fechaSolicitud)}</span>
      </div>

      <p className="persona">{otra?.nombre ?? 'Alguien de la comunidad'}</p>
      <p className="detalle">
        {esRecibida ? 'quiere compartir contacto contigo' : 'le pediste contacto'}
        {otra?.carrera ? ` · ${otra.carrera}` : ''}
      </p>
      {solicitud.proyectoNombre && (
        <p className="sobre-proyecto">Sobre <strong>{solicitud.proyectoNombre}</strong></p>
      )}
      {solicitud.mensaje && <p className="cita">“{solicitud.mensaje}”</p>}

      {puedeResponder && (
        <div className="acciones">
          <button className="principal" onClick={() => alResponder(solicitud, true)}>Aceptar</button>
          <button className="secundario" onClick={() => alResponder(solicitud, false)}>Rechazar</button>
        </div>
      )}
    </div>
  )
}

/**
 * Contactos desbloqueados, en las dos direcciones: da igual quién pidió, importa que hubo
 * aceptación. Los datos se abren en una ventana y no se muestran en la tarjeta: son datos que la
 * otra persona compartió a propósito.
 */
export function MisColaboraciones({ recibidas, enviadas }) {
  // Cuál está abierta en la ventana. null = ninguna.
  const [abierta, setAbierta] = useState(null)

  if (recibidas === null || enviadas === null) return <p className="ayuda">Cargando…</p>

  const aceptadas = [
    ...enviadas.filter((s) => s.estado === 'ACEPTADA').map((s) => ({ s, otra: s.solicitado })),
    ...recibidas.filter((s) => s.estado === 'ACEPTADA').map((s) => ({ s, otra: s.solicitante })),
  ]

  if (aceptadas.length === 0) {
    return <p className="ayuda">Cuando aceptes una solicitud, o te acepten una, el contacto aparecerá aquí.</p>
  }

  return (
    <>
      <div className="lista">
        {aceptadas.map(({ s, otra }) => (
          <div key={s.id} className="contacto">
            <p className="persona">{otra?.nombre ?? 'Alguien de la comunidad'}</p>
            <p className="detalle">{[otra?.carrera, otra?.sede].filter(Boolean).join(' · ')}</p>
            {s.proyectoNombre && (
              <p className="sobre-proyecto">Sobre <strong>{s.proyectoNombre}</strong></p>
            )}
            <div className="acciones">
              <button className="secundario" onClick={() => setAbierta({ s, otra })}>
                Ver datos de contacto
              </button>
            </div>
          </div>
        ))}
      </div>

      {abierta && (
        <DatosDeContacto
          colaboracion={abierta.s}
          persona={abierta.otra}
          alCerrar={() => setAbierta(null)}
        />
      )}
    </>
  )
}

/**
 * Al aceptar no se escribe nada: el BFF arma los datos con el perfil propio.
 *
 * <p>La decisión sobre el teléfono la toma quien acepta en la ventana de confirmación, así que
 * llega ya resuelta. Rechazar no comparte nada y no necesita confirmación.</p>
 */
export async function responder(solicitud, aceptar, compartirTelefono = false) {
  await responderColaboracion(solicitud.id, aceptar, compartirTelefono)
}
