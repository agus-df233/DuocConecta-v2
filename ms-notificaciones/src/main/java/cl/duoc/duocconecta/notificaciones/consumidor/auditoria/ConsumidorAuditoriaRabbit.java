package cl.duoc.duocconecta.notificaciones.consumidor.auditoria;

import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import cl.duoc.duocconecta.notificaciones.consumidor.ConfirmacionRabbit;
import cl.duoc.duocconecta.notificaciones.consumidor.MensajeRabbit;
import cl.duoc.duocconecta.notificaciones.consumidor.ProcesadorDeMensajes;
import cl.duoc.duocconecta.notificaciones.service.ServicioAuditoria;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consume la cola sin filtro ({@code #}) desde RabbitMQ: registra todo evento. Mismo nombre de
 * consumidor que {@link ConsumidorAuditoria}, así un evento que llega por los dos brokers queda
 * auditado una sola vez.
 */
@Component
@ConditionalOnProperty(prefix = "duocconecta.mensajeria.rabbit", name = "habilitada", havingValue = "true")
public class ConsumidorAuditoriaRabbit {

    private final ProcesadorDeMensajes procesador;
    private final ServicioAuditoria auditoria;
    private final PropiedadesMensajeria.Rabbit rabbit;

    public ConsumidorAuditoriaRabbit(ProcesadorDeMensajes procesador, ServicioAuditoria auditoria,
                                     PropiedadesMensajeria propiedades) {
        this.procesador = procesador;
        this.auditoria = auditoria;
        this.rabbit = propiedades.getRabbit();
    }

    /** ACK manual: lo decide {@link ProcesadorDeMensajes} con {@link ConfirmacionRabbit}. */
    @RabbitListener(id = "rabbit-auditoria", queues = "${duocconecta.mensajeria.rabbit.colas.auditoria}", ackMode = "MANUAL")
    public void recibir(Message mensaje, Channel canal) {
        MensajeRabbit m = MensajeRabbit.de(mensaje);
        procesador.procesar(ConsumidorAuditoria.NOMBRE, m.cuerpo(), m.idPeticion(), m.intento(),
                new ConfirmacionRabbit(canal, m.etiqueta(), rabbit.getMaxEntregas(), rabbit.descarte("auditoria")),
                evento -> auditoria.registrar(evento, m.idPeticion()));
    }
}
