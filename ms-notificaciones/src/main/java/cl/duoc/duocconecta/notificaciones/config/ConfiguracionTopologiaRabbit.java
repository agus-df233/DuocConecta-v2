package cl.duoc.duocconecta.notificaciones.config;

import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La topología de RabbitMQ, una ruta por bean: Exchange, Queue y Binding. RabbitAdmin la declara
 * en el broker al conectarse (si ya existe igual, no la toca). Los nombres salen de
 * {@code duocconecta.mensajeria.rabbit} en el YAML.
 *
 * <pre>
 * topic duocconecta.eventos ─┬─ proyecto.#     ─► duocconecta.notificaciones.proyectos      ─┐
 *  (routing key = tipo)      ├─ colaboracion.# ─► duocconecta.notificaciones.colaboraciones ─┼─ rechazo o
 *                            └─ #              ─► duocconecta.auditoria                     ─┘  3 entregas
 *                                                                                              │
 * direct duocconecta.dlx ◄─────────────────────────────────────────────────────────────────────┘
 *  └─ routing key = nombre de la DLQ ─► …-descartes (una por cola)
 * </pre>
 *
 * <p>Las colas son <em>quorum</em> aunque haya un solo nodo: son las que cuentan las entregas
 * ({@code x-delivery-limit}). Al pasar el límite, RabbitMQ manda el mensaje al DLX por su cuenta.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "duocconecta.mensajeria.rabbit", name = "habilitada", havingValue = "true")
public class ConfiguracionTopologiaRabbit {

    private final PropiedadesMensajeria.Rabbit rabbit;

    public ConfiguracionTopologiaRabbit(PropiedadesMensajeria propiedades) {
        this.rabbit = propiedades.getRabbit();
    }

    // ---------------------------------------------------------------- exchanges

    /** Topic: entrega una copia a cada cola cuyo binding calce con el tipo del evento. */
    @Bean
    public TopicExchange rabbitExchangeEventos() {
        return new TopicExchange(rabbit.getExchange(), true, false);
    }

    /** Direct: lo rechazado llega con la routing key de su DLQ y va exactamente a esa cola. */
    @Bean
    public DirectExchange rabbitExchangeDescartes() {
        return new DirectExchange(rabbit.getDlx(), true, false);
    }

    // ---------------------------------------------------------------- DLQ

    @Bean
    public Queue rabbitDescartesProyectos() {
        return descarte("proyectos");
    }

    @Bean
    public Queue rabbitDescartesColaboraciones() {
        return descarte("colaboraciones");
    }

    @Bean
    public Queue rabbitDescartesAuditoria() {
        return descarte("auditoria");
    }

    @Bean
    public Binding descartesProyectosDesdeDlx() {
        return BindingBuilder.bind(rabbitDescartesProyectos()).to(rabbitExchangeDescartes()).with(rabbit.descarte("proyectos"));
    }

    @Bean
    public Binding descartesColaboracionesDesdeDlx() {
        return BindingBuilder.bind(rabbitDescartesColaboraciones()).to(rabbitExchangeDescartes()).with(rabbit.descarte("colaboraciones"));
    }

    @Bean
    public Binding descartesAuditoriaDesdeDlx() {
        return BindingBuilder.bind(rabbitDescartesAuditoria()).to(rabbitExchangeDescartes()).with(rabbit.descarte("auditoria"));
    }

    // ---------------------------------------------------------------- colas de trabajo

    /** Avisos por comentarios, colaboradores y moderación. */
    @Bean
    public Queue rabbitColaProyectos() {
        return trabajo("proyectos");
    }

    /** Avisos de solicitudes de colaboración y sus respuestas. */
    @Bean
    public Queue rabbitColaColaboraciones() {
        return trabajo("colaboraciones");
    }

    /** Registro de todo evento. */
    @Bean
    public Queue rabbitColaAuditoria() {
        return trabajo("auditoria");
    }

    // ---------------------------------------------------------------- bindings

    /** {@code proyecto.#}: publicado, comentado, ocultado… y cualquier evento de proyecto futuro. */
    @Bean
    public Binding rabbitProyectosEscuchaProyecto() {
        return BindingBuilder.bind(rabbitColaProyectos()).to(rabbitExchangeEventos()).with(rabbit.binding("proyectos"));
    }

    /** {@code colaboracion.#}: solicitada, aceptada y rechazada. */
    @Bean
    public Binding rabbitColaboracionesEscuchaColaboracion() {
        return BindingBuilder.bind(rabbitColaColaboraciones()).to(rabbitExchangeEventos()).with(rabbit.binding("colaboraciones"));
    }

    /** {@code #}: todo, incluidos los eventos que aún no existen. */
    @Bean
    public Binding rabbitAuditoriaEscuchaTodo() {
        return BindingBuilder.bind(rabbitColaAuditoria()).to(rabbitExchangeEventos()).with(rabbit.binding("auditoria"));
    }

    private Queue trabajo(String clave) {
        return QueueBuilder.durable(rabbit.cola(clave))
                .quorum()
                // x-delivery-limit cuenta las DEVOLUCIONES, no las entregas: 3 entregas = 2 devoluciones.
                .deliveryLimit(Math.max(0, rabbit.getMaxEntregas() - 1))
                .deadLetterExchange(rabbit.getDlx())
                .deadLetterRoutingKey(rabbit.descarte(clave))
                .build();
    }

    private Queue descarte(String clave) {
        return QueueBuilder.durable(rabbit.descarte(clave)).quorum().build();
    }
}
