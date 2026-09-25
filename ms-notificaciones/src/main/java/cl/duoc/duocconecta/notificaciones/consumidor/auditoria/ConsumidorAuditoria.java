package cl.duoc.duocconecta.notificaciones.consumidor.auditoria;

import cl.duoc.duocconecta.comun.eventos.AtributosMensaje;
import cl.duoc.duocconecta.notificaciones.consumidor.ProcesadorDeMensajes;
import cl.duoc.duocconecta.notificaciones.service.ServicioAuditoria;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.Visibility;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consume la cola sin filtro, el {@code #} del laboratorio: registra todo evento, incluidos los
 * que todavía no existen en el catálogo.
 */
@Component
public class ConsumidorAuditoria {

    static final String NOMBRE = "auditoria";

    private final ProcesadorDeMensajes procesador;
    private final ServicioAuditoria auditoria;

    public ConsumidorAuditoria(ProcesadorDeMensajes procesador, ServicioAuditoria auditoria) {
        this.procesador = procesador;
        this.auditoria = auditoria;
    }

    /** Confirmación manual: la decide {@link ProcesadorDeMensajes}. */
    @SqsListener(value = "${duocconecta.mensajeria.colas.auditoria}", acknowledgementMode = "MANUAL")
    public void recibir(String cuerpo,
                        @Header(name = AtributosMensaje.ID_PETICION, required = false) String idPeticion,
                        @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
                                required = false) String intento,
                        Acknowledgement ack, Visibility visibilidad) {
        procesador.procesar(NOMBRE, "auditoria", cuerpo, idPeticion, intento, ack, visibilidad,
                evento -> auditoria.registrar(evento, idPeticion));
    }
}
