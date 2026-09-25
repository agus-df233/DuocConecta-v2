package cl.duoc.duocconecta.notificaciones.consumidor.proyectos;

import cl.duoc.duocconecta.comun.eventos.AtributosMensaje;
import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.comun.eventos.TiposDeEvento;
import cl.duoc.duocconecta.notificaciones.consumidor.ProcesadorDeMensajes;
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
 * Consume {@code proyecto.*}: avisa cuando comentan tu proyecto, te suman como colaborador o la
 * moderación actúa sobre algo tuyo. Lo que hace la propia dueña (publicar, editar) no se avisa.
 */
@Component
public class ConsumidorProyectos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorProyectos.class);

    static final String NOMBRE = "proyectos";

    /** Valor de {@code detalle} con que ms-proyectos marca un borrado hecho por la moderación. */
    private static final String BORRADO_POR_MODERACION = "moderacion";

    private final ProcesadorDeMensajes procesador;
    private final ServicioAvisos avisos;

    public ConsumidorProyectos(ProcesadorDeMensajes procesador, ServicioAvisos avisos) {
        this.procesador = procesador;
        this.avisos = avisos;
    }

    /** Confirmación manual: la decide {@link ProcesadorDeMensajes}. */
    @SqsListener(value = "${duocconecta.mensajeria.colas.proyectos}", acknowledgementMode = "MANUAL")
    public void recibir(String cuerpo,
                        @Header(name = AtributosMensaje.ID_PETICION, required = false) String idPeticion,
                        @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
                                required = false) String intento,
                        Acknowledgement ack, Visibility visibilidad) {
        procesador.procesar(NOMBRE, "proyectos", cuerpo, idPeticion, intento, ack, visibilidad, this::atender);
    }

    void atender(EventoDuocConecta evento) {
        String titulo = titulo(evento);
        if (titulo == null) {
            log.info("Evento '{}' sin aviso que dar: se confirma sin acción", evento.tipo());
            return;
        }
        // Nadie se avisa a sí mismo (por ejemplo, la dueña comentando su propio proyecto).
        if (evento.destinatarioId() == null || evento.destinatarioId().equals(evento.actorId())) {
            log.info("Evento '{}' sin otra persona a quien avisar", evento.tipo());
            return;
        }
        avisos.crear(evento, titulo);
    }

    private static String titulo(EventoDuocConecta evento) {
        return switch (evento.tipo()) {
            case TiposDeEvento.PROYECTO_COMENTADO -> "Comentaron tu proyecto";
            case TiposDeEvento.PROYECTO_COLABORADOR_AGREGADO -> "Te agregaron como colaborador de un proyecto";
            case TiposDeEvento.PROYECTO_OCULTADO -> "Tu proyecto fue ocultado por moderación";
            case TiposDeEvento.PROYECTO_RESTAURADO -> "Tu proyecto volvió a la vitrina";
            case TiposDeEvento.PROYECTO_COMENTARIO_ELIMINADO -> "Un comentario tuyo fue eliminado por moderación";
            case TiposDeEvento.PROYECTO_ELIMINADO ->
                    BORRADO_POR_MODERACION.equals(evento.detalle()) ? "Tu proyecto fue eliminado por moderación" : null;
            default -> null;
        };
    }
}
