package cl.duoc.duocconecta.notificaciones.consumidor.colaboraciones;

import cl.duoc.duocconecta.comun.eventos.AtributosMensaje;
import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.comun.eventos.TiposDeEvento;
import cl.duoc.duocconecta.notificaciones.consumidor.ProcesadorDeMensajes;
import cl.duoc.duocconecta.notificaciones.exception.EventoNoProcesableException;
import cl.duoc.duocconecta.notificaciones.service.ServicioAvisos;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.Visibility;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consume {@code colaboracion.*}: avisa a quien recibe una solicitud y a quien la pidió cuando se
 * responde. El correo real queda para la EP3 (SES en el lab solo envía a direcciones verificadas).
 */
@Component
public class ConsumidorColaboraciones {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorColaboraciones.class);

    /** Nombre del consumidor en el registro de eventos procesados. */
    static final String NOMBRE = "colaboraciones";

    private final ProcesadorDeMensajes procesador;
    private final ServicioAvisos avisos;

    public ConsumidorColaboraciones(ProcesadorDeMensajes procesador, ServicioAvisos avisos) {
        this.procesador = procesador;
        this.avisos = avisos;
    }

    /** Confirmación manual: la decide {@link ProcesadorDeMensajes}. */
    @SqsListener(value = "${duocconecta.mensajeria.colas.colaboraciones}", acknowledgementMode = "MANUAL")
    public void recibir(String cuerpo,
                        @Header(name = AtributosMensaje.ID_PETICION, required = false) String idPeticion,
                        @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
                                required = false) String intento,
                        Acknowledgement ack, Visibility visibilidad) {
        procesador.procesar(NOMBRE, "colaboraciones", cuerpo, idPeticion, intento, ack, visibilidad, this::atender);
    }

    void atender(EventoDuocConecta evento) {
        String titulo = switch (evento.tipo()) {
            case TiposDeEvento.COLABORACION_SOLICITADA -> "Tienes una nueva solicitud de colaboración";
            case TiposDeEvento.COLABORACION_ACEPTADA -> "Aceptaron tu solicitud de colaboración";
            case TiposDeEvento.COLABORACION_RECHAZADA -> "Rechazaron tu solicitud de colaboración";
            default -> null;
        };
        if (titulo == null) {
            // Un evento nuevo de la categoría llega sin tocar la suscripción: se confirma sin acción.
            log.info("Evento '{}' sin manejador en {}: se confirma sin acción", evento.tipo(), NOMBRE);
            return;
        }
        if (evento.destinatarioId() == null || evento.destinatarioId().isBlank()) {
            throw new EventoNoProcesableException("Evento '" + evento.tipo() + "' sin destinatario");
        }
        avisos.crear(evento, titulo);
        log.info("Correo (simulado) a {}: {}", evento.destinatarioId(), titulo);
    }
}
