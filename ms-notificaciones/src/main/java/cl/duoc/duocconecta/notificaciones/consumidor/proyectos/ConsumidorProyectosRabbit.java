package cl.duoc.duocconecta.notificaciones.consumidor.proyectos;

import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import cl.duoc.duocconecta.notificaciones.consumidor.ConfirmacionRabbit;
import cl.duoc.duocconecta.notificaciones.consumidor.MensajeRabbit;
import cl.duoc.duocconecta.notificaciones.consumidor.ProcesadorDeMensajes;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consume {@code proyecto.#} desde RabbitMQ. Hace lo mismo que {@link ConsumidorProyectos} (que
 * escucha SQS) y con el mismo nombre de consumidor: si un evento llega por los dos brokers, la
 * tabla de procesados lo deja pasar una sola vez.
 */
@Component
@ConditionalOnProperty(prefix = "duocconecta.mensajeria.rabbit", name = "habilitada", havingValue = "true")
public class ConsumidorProyectosRabbit {

    private final ProcesadorDeMensajes procesador;
    private final ConsumidorProyectos dominio;
    private final PropiedadesMensajeria.Rabbit rabbit;

    public ConsumidorProyectosRabbit(ProcesadorDeMensajes procesador, ConsumidorProyectos dominio,
                                     PropiedadesMensajeria propiedades) {
        this.procesador = procesador;
        this.dominio = dominio;
        this.rabbit = propiedades.getRabbit();
    }

    /** ACK manual: lo decide {@link ProcesadorDeMensajes} con {@link ConfirmacionRabbit}. */
    @RabbitListener(id = "rabbit-proyectos", queues = "${duocconecta.mensajeria.rabbit.colas.proyectos}", ackMode = "MANUAL")
    public void recibir(Message mensaje, Channel canal) {
        MensajeRabbit m = MensajeRabbit.de(mensaje);
        procesador.procesar(ConsumidorProyectos.NOMBRE, m.cuerpo(), m.idPeticion(), m.intento(),
                new ConfirmacionRabbit(canal, m.etiqueta(), rabbit.getMaxEntregas(), rabbit.descarte("proyectos")),
                dominio::atender);
    }
}
