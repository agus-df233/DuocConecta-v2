package cl.duoc.duocconecta.comun.eventos;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica en el exchange topic de RabbitMQ, con el tipo del evento como routing key: así cada
 * binding ({@code proyecto.#}, {@code colaboracion.#}, {@code #}) decide qué cola lo recibe.
 *
 * <p>El cuerpo es el mismo JSON que va a SNS, y las cabeceras son las mismas que los atributos de
 * SNS. El mensaje es persistente: sobrevive a un reinicio del broker.</p>
 */
public class PublicadorRabbit implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorRabbit.class);

    private final RabbitTemplate plantilla;
    private final JsonMapper jsonMapper;
    private final PropiedadesMensajeria propiedades;

    public PublicadorRabbit(RabbitTemplate plantilla, JsonMapper jsonMapper, PropiedadesMensajeria propiedades) {
        this.plantilla = plantilla;
        this.jsonMapper = jsonMapper;
        this.propiedades = propiedades;
    }

    @Override
    public void publicar(EventoDuocConecta eventoDelNegocio) {
        EventoDuocConecta evento = eventoDelNegocio.origen() != null
                ? eventoDelNegocio : eventoDelNegocio.conOrigen(propiedades.getOrigen());

        MessageProperties cabeceras = new MessageProperties();
        cabeceras.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        cabeceras.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        cabeceras.setMessageId(evento.idEvento());
        cabeceras.setHeader(AtributosMensaje.TIPO, evento.tipo());
        cabeceras.setHeader(AtributosMensaje.ORIGEN, evento.origen());
        String idPeticion = MDC.get(AtributosMensaje.CLAVE_MDC);
        if (idPeticion != null && !idPeticion.isBlank()) {
            cabeceras.setHeader(AtributosMensaje.ID_PETICION, idPeticion);
        }

        Message mensaje = new Message(jsonMapper.writeValueAsBytes(evento), cabeceras);
        // La confirmación del broker (publisher confirm) llega en otro hilo y queda en el log.
        plantilla.send(propiedades.getRabbit().getExchange(), evento.tipo(), mensaje,
                new CorrelationData(evento.idEvento() != null ? evento.idEvento() : UUID.randomUUID().toString()));
        log.info("Evento {} '{}' publicado en RabbitMQ ({})", evento.idEvento(), evento.tipo(),
                propiedades.getRabbit().getExchange());
    }
}
