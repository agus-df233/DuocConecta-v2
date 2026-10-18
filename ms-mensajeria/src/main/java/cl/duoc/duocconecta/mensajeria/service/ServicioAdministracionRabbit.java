package cl.duoc.duocconecta.mensajeria.service;

import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.BindingDatos;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.ColaRabbitRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.CrearColaRabbitDatos;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.CrearExchangeDatos;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.ExchangeRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.PurgaRabbitRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.RedriveRabbitRespuesta;
import cl.duoc.duocconecta.mensajeria.exception.ConflictoDeEstadoException;
import cl.duoc.duocconecta.mensajeria.exception.RecursoNoEncontradoException;
import cl.duoc.duocconecta.mensajeria.exception.SolicitudInvalidaException;
import com.rabbitmq.client.GetResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Administración de RabbitMQ: crear, listar, purgar y eliminar colas, exchanges y bindings, y el
 * redrive de una DLQ. Toda la lógica vive aquí y sobre {@link AmqpAdmin} y {@link RabbitTemplate}:
 * los controladores no conocen RabbitMQ.
 *
 * <p>La topología base (el exchange de eventos, el DLX, las 3 colas y sus DLQ) está protegida, y
 * la API solo toca recursos con el prefijo {@code duocconecta.}.</p>
 */
@Service
@ConditionalOnProperty(prefix = "duocconecta.mensajeria.rabbit", name = "habilitada", havingValue = "true")
public class ServicioAdministracionRabbit {

    private static final Logger log = LoggerFactory.getLogger(ServicioAdministracionRabbit.class);

    private static final String PREFIJO = "duocconecta.";
    private static final String SUFIJO_DESCARTE = ".descartes";

    /** Tope de mensajes por redrive: una DLQ grande se devuelve en varias llamadas, no en una eterna. */
    static final int TOPE_REDRIVE = 100;

    private final AmqpAdmin admin;
    private final RabbitTemplate plantilla;
    private final PropiedadesMensajeria.Rabbit rabbit;
    private final Set<String> protegidos = new HashSet<>();

    public ServicioAdministracionRabbit(AmqpAdmin admin, RabbitTemplate plantilla, PropiedadesMensajeria propiedades) {
        this.admin = admin;
        this.plantilla = plantilla;
        this.rabbit = propiedades.getRabbit();
        protegidos.add(rabbit.getExchange());
        protegidos.add(rabbit.getDlx());
        protegidos.addAll(rabbit.getColas().values());
        protegidos.addAll(rabbit.getDescartes().values());
    }

    /**
     * Las colas de la topología base con sus contadores. AMQP no tiene una operación para listar
     * todas las colas del broker; las creadas por la API se consultan por nombre.
     */
    public List<ColaRabbitRespuesta> listarColas() {
        return Stream.concat(rabbit.getColas().values().stream(), rabbit.getDescartes().values().stream())
                .filter(nombre -> admin.getQueueInfo(nombre) != null)
                .sorted()
                .map(this::detalleCola)
                .toList();
    }

    /** Contadores de una cola. 404 si no existe. */
    public ColaRabbitRespuesta detalleCola(String nombre) {
        exigirDeLaPlataforma(nombre);
        QueueInformation info = admin.getQueueInfo(nombre);
        if (info == null) {
            throw new RecursoNoEncontradoException("La cola " + nombre + " no existe");
        }
        String dlq = admin.getQueueInfo(nombre + SUFIJO_DESCARTE) != null ? nombre + SUFIJO_DESCARTE : null;
        return new ColaRabbitRespuesta(nombre, info.getMessageCount(), info.getConsumerCount(), dlq,
                protegidos.contains(nombre));
    }

    /**
     * Crea una cola quorum. Con DLQ: también crea {@code <nombre>.descartes}, la enlaza al DLX y
     * declara la cola con {@code x-dead-letter-exchange} y {@code x-delivery-limit}. 409 si existe.
     */
    public ColaRabbitRespuesta crearCola(CrearColaRabbitDatos datos) {
        if (admin.getQueueInfo(datos.nombre()) != null) {
            throw new ConflictoDeEstadoException("La cola " + datos.nombre() + " ya existe");
        }
        QueueBuilder cola = QueueBuilder.durable(datos.nombre()).quorum();
        if (datos.quiereDescarte()) {
            String dlq = datos.nombre() + SUFIJO_DESCARTE;
            admin.declareQueue(QueueBuilder.durable(dlq).quorum().build());
            admin.declareExchange(new DirectExchange(rabbit.getDlx(), true, false));
            admin.declareBinding(new Binding(dlq, Binding.DestinationType.QUEUE, rabbit.getDlx(), dlq, Map.of()));
            cola.deadLetterExchange(rabbit.getDlx()).deadLetterRoutingKey(dlq)
                    // x-delivery-limit cuenta devoluciones: N entregas = N - 1 devoluciones.
                    .deliveryLimit(Math.max(0, (datos.maxEntregas() != null ? datos.maxEntregas() : rabbit.getMaxEntregas()) - 1));
        }
        admin.declareQueue(cola.build());
        log.info("Administración RabbitMQ: cola {} creada{}", datos.nombre(), datos.quiereDescarte() ? " con su DLQ" : "");
        return detalleCola(datos.nombre());
    }

    /** Elimina una cola. Las de la topología base están protegidas. */
    public void eliminarCola(String nombre) {
        exigirDeLaPlataforma(nombre);
        exigirNoProtegido(nombre);
        if (admin.getQueueInfo(nombre) == null) {
            throw new RecursoNoEncontradoException("La cola " + nombre + " no existe");
        }
        admin.deleteQueue(nombre);
        log.info("Administración RabbitMQ: cola {} eliminada", nombre);
    }

    /** Vacía una cola. Sirve para limpiar una DLQ después de revisarla. 404 si no existe. */
    public PurgaRabbitRespuesta purgarCola(String nombre) {
        exigirDeLaPlataforma(nombre);
        exigirExistente(nombre);
        int borrados = admin.purgeQueue(nombre);
        log.info("Administración RabbitMQ: cola {} purgada ({} mensajes)", nombre, borrados);
        return new PurgaRabbitRespuesta(nombre, borrados);
    }

    /**
     * Devuelve los mensajes de una DLQ ({@code <cola>.descartes}) a su cola, por el exchange por
     * defecto. Cada mensaje se confirma en la DLQ solo después de publicarlo en la cola: si la
     * publicación falla, vuelve a la DLQ y no se pierde. Llegan como mensajes nuevos, así que la
     * cola quorum vuelve a contar sus entregas desde cero.
     */
    public RedriveRabbitRespuesta redrive(String dlq) {
        exigirDeLaPlataforma(dlq);
        if (!dlq.endsWith(SUFIJO_DESCARTE)) {
            throw new SolicitudInvalidaException("El redrive se hace desde una DLQ (" + SUFIJO_DESCARTE + "): " + dlq);
        }
        String destino = dlq.substring(0, dlq.length() - SUFIJO_DESCARTE.length());
        exigirExistente(dlq);
        exigirExistente(destino);
        Integer movidos = plantilla.execute(canal -> {
            int n = 0;
            GetResponse respuesta;
            while (n < TOPE_REDRIVE && (respuesta = canal.basicGet(dlq, false)) != null) {
                long etiqueta = respuesta.getEnvelope().getDeliveryTag();
                try {
                    canal.basicPublish("", destino, respuesta.getProps(), respuesta.getBody());
                    canal.basicAck(etiqueta, false);
                    n++;
                } catch (RuntimeException | IOException error) {
                    canal.basicNack(etiqueta, false, true);
                    throw error;
                }
            }
            return n;
        });
        int total = Objects.requireNonNullElse(movidos, 0);
        log.info("Administración RabbitMQ: redrive de {} a {} ({} mensajes)", dlq, destino, total);
        return new RedriveRabbitRespuesta(dlq, destino, total);
    }

    /** Crea un exchange durable del tipo pedido. Declarar uno igual que ya existe no cambia nada. */
    public ExchangeRespuesta crearExchange(CrearExchangeDatos datos) {
        exigirNoProtegido(datos.nombre());
        Exchange exchange = switch (datos.tipo()) {
            case DIRECT -> new DirectExchange(datos.nombre(), true, false);
            case TOPIC -> new TopicExchange(datos.nombre(), true, false);
            case FANOUT -> new FanoutExchange(datos.nombre(), true, false);
        };
        admin.declareExchange(exchange);
        log.info("Administración RabbitMQ: exchange {} ({}) creado", datos.nombre(), datos.tipo());
        return new ExchangeRespuesta(datos.nombre(), datos.tipo());
    }

    /** Elimina un exchange. Los de la topología base están protegidos. */
    public void eliminarExchange(String nombre) {
        exigirDeLaPlataforma(nombre);
        exigirNoProtegido(nombre);
        admin.deleteExchange(nombre);
        log.info("Administración RabbitMQ: exchange {} eliminado", nombre);
    }

    /** Une una cola a un exchange con una routing key (o un patrón, si el exchange es topic). */
    public BindingDatos crearBinding(BindingDatos datos) {
        if (admin.getQueueInfo(datos.cola()) == null) {
            throw new RecursoNoEncontradoException("La cola " + datos.cola() + " no existe");
        }
        admin.declareBinding(binding(datos));
        log.info("Administración RabbitMQ: {} enlazada a {} con '{}'", datos.cola(), datos.exchange(), datos.routingKey());
        return datos;
    }

    /** Quita un binding. Los de la topología base están protegidos. */
    public void eliminarBinding(BindingDatos datos) {
        if (protegidos.contains(datos.exchange()) && protegidos.contains(datos.cola())) {
            throw new ConflictoDeEstadoException("Ese binding es parte de la topología base y no se elimina desde la API");
        }
        admin.removeBinding(binding(datos));
        log.info("Administración RabbitMQ: binding {} → {} ('{}') eliminado", datos.exchange(), datos.cola(), datos.routingKey());
    }

    private static Binding binding(BindingDatos datos) {
        return new Binding(datos.cola(), Binding.DestinationType.QUEUE, datos.exchange(), datos.routingKey(), Map.of());
    }

    /** Los nombres por ruta no pasan por el DTO: se revisa aquí que sean de la plataforma. */
    private static void exigirDeLaPlataforma(String nombre) {
        if (nombre == null || !nombre.startsWith(PREFIJO)) {
            throw new SolicitudInvalidaException("Solo se administran recursos con el prefijo " + PREFIJO
                    + ": " + nombre + " no es de la plataforma");
        }
    }

    private void exigirExistente(String nombre) {
        if (admin.getQueueInfo(nombre) == null) {
            throw new RecursoNoEncontradoException("La cola " + nombre + " no existe");
        }
    }

    private void exigirNoProtegido(String nombre) {
        if (protegidos.contains(nombre)) {
            throw new ConflictoDeEstadoException(nombre + " es parte de la topología base y no se modifica desde la API");
        }
    }
}
