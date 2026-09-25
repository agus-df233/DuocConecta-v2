// Ventana con los datos que la otra persona compartió.
//
// Van detrás de un botón y no a la vista en la tarjeta: son datos que esa persona compartió a
// propósito, y no tienen por qué quedar en pantalla mientras alguien mira por encima del hombro.
//
// El campo `contacto` lo resuelve el BFF, que es quien sabe quién está mirando. Antes llegaban los
// dos lados y esta pantalla elegía uno: en las solicitudes recibidas elegía mal y mostraba los
// datos propios con el nombre de la otra persona.
import { iniciales } from './catalogo'

export default function DatosDeContacto({ colaboracion, persona, alCerrar }) {
  const nombre = persona?.nombre ?? 'Alguien de la comunidad'

  return (
    <>
      <div className="modal-fondo" onClick={alCerrar} />
      <div className="modal-caja">
        <div className="modal">
          <div className="modal-cabecera">
            <h2>Datos de contacto</h2>
            <button className="cerrar" onClick={alCerrar} aria-label="Cerrar">×</button>
          </div>

          <div className="cuerpo-modal">
            <div className="destinatario">
              <div className="iniciales">{iniciales(persona?.nombre)}</div>
              <div>
                <strong>{nombre}</strong>
                <span>{[persona?.carrera, persona?.sede].filter(Boolean).join(' · ')}</span>
              </div>
            </div>

            <div className="contacto">
              <dl>
                <Dato etiqueta="Correo" valor={colaboracion.contacto?.correo} />
                <Dato etiqueta="Teléfono" valor={colaboracion.contacto?.telefono} />
                <Dato etiqueta="Redes" valor={colaboracion.contacto?.redes} />
              </dl>
            </div>

            <div className="consentimiento">
              <h4>Los compartió contigo</h4>
              <p>
                {nombre.split(' ')[0]} eligió qué mostrar al aceptar. Un campo vacío significa que
                decidió no compartirlo, no que no lo tenga.
              </p>
            </div>

            <div className="acciones">
              <button className="secundario" type="button" onClick={alCerrar}>Cerrar</button>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}

/** Una fila de contacto. Si no vino el dato, se dice explícitamente que no se compartió. */
function Dato({ etiqueta, valor }) {
  return (
    <div className="dato">
      <dt>{etiqueta}</dt>
      <dd className={valor ? '' : 'sin'}>{valor || 'no lo compartió'}</dd>
    </div>
  )
}
