package cl.duoc.duocconecta.notificaciones.controller;

import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
import cl.duoc.duocconecta.notificaciones.dto.EstadoCola;
import cl.duoc.duocconecta.notificaciones.dto.EstadoConsumidor;
import cl.duoc.duocconecta.notificaciones.dto.MensajeDescartado;
import cl.duoc.duocconecta.notificaciones.service.MonitorDescartes;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de la mensajería de este servicio, solo para ADMINISTRADOR: estado de las DLQ y
 * el interruptor de los consumidores, que sirve para demostrar el desacoplamiento en Fargate
 * sin detener la tarea. La pausa cubre los dos brokers: si solo se detuviera SQS, RabbitMQ
 * entregaría el mismo evento y el aviso llegaría igual.
 */
@RestController
@RequestMapping("/api/v1/notificaciones/admin")
@PreAuthorize(ReglasDeAcceso.SOLO_ADMINISTRADOR)
@Tag(name = "Administración de notificaciones", description = "DLQ y consumidores. Solo ADMINISTRADOR")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorAdministracionNotificaciones {

    private static final Logger log = LoggerFactory.getLogger(ControladorAdministracionNotificaciones.class);

    private final MonitorDescartes monitor;
    private final MessageListenerContainerRegistry consumidoresSqs;
    private final ObjectProvider<RabbitListenerEndpointRegistry> consumidoresRabbit;

    public ControladorAdministracionNotificaciones(MonitorDescartes monitor,
                                                   MessageListenerContainerRegistry consumidoresSqs,
                                                   ObjectProvider<RabbitListenerEndpointRegistry> consumidoresRabbit) {
        this.monitor = monitor;
        this.consumidoresSqs = consumidoresSqs;
        this.consumidoresRabbit = consumidoresRabbit;
    }

    /** Cuántos mensajes hay en cada DLQ, de SQS y de RabbitMQ. */
    @Operation(summary = "Estado de las colas de descarte")
    @GetMapping("/descartes")
    public List<EstadoCola> descartes() {
        return monitor.estado();
    }

    /**
     * Hasta 10 mensajes de una DLQ de SQS, sin sacarlos de ahí. En RabbitMQ no hay forma de mirar
     * sin tomar el mensaje y devolverlo, lo que alteraría la DLQ: ahí se ve la consola.
     */
    @Operation(summary = "Mira los mensajes de una DLQ de SQS sin consumirlos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Muestra de mensajes"),
            @ApiResponse(responseCode = "404", description = "No es una DLQ de este servicio", content = @Content)
    })
    @GetMapping("/descartes/{cola}/mensajes")
    public List<MensajeDescartado> mensajes(@PathVariable String cola) {
        return monitor.muestra(cola);
    }

    /** Cada consumidor de los dos brokers y si está consumiendo. */
    @Operation(summary = "Estado de los consumidores")
    @GetMapping("/consumidores")
    public List<EstadoConsumidor> estadoConsumidores() {
        Stream<EstadoConsumidor> sqs = consumidoresSqs.getListenerContainers().stream()
                .map(c -> new EstadoConsumidor(MonitorDescartes.SQS, c.getId(), c.isRunning()));
        Stream<EstadoConsumidor> rabbit = registroRabbit().stream()
                .flatMap(r -> r.getListenerContainerIds().stream()
                        .map(id -> new EstadoConsumidor(MonitorDescartes.RABBIT, id, r.getListenerContainer(id).isRunning())));
        return Stream.concat(sqs, rabbit)
                .sorted(Comparator.comparing(EstadoConsumidor::broker).thenComparing(EstadoConsumidor::id))
                .toList();
    }

    /** Deja de consumir en los dos brokers: los mensajes se acumulan en las colas, nada se pierde. */
    @Operation(summary = "Pausa los consumidores", description = "SQS y RabbitMQ. Los eventos siguen llegando y esperan en las colas.")
    @PostMapping("/consumidores/pausa")
    public List<EstadoConsumidor> pausar() {
        consumidoresSqs.getListenerContainers().forEach(c -> c.stop());
        registroRabbit().ifPresent(RabbitListenerEndpointRegistry::stop);
        log.warn("Consumidores de SQS y RabbitMQ pausados por un administrador");
        return estadoConsumidores();
    }

    /** Vuelve a consumir en los dos brokers: lo acumulado se procesa solo. */
    @Operation(summary = "Reanuda los consumidores")
    @PostMapping("/consumidores/reanudar")
    public List<EstadoConsumidor> reanudar() {
        consumidoresSqs.getListenerContainers().forEach(c -> c.start());
        registroRabbit().ifPresent(RabbitListenerEndpointRegistry::start);
        log.info("Consumidores de SQS y RabbitMQ reanudados por un administrador");
        return estadoConsumidores();
    }

    /** El registro de RabbitMQ solo existe con el segundo broker habilitado. */
    private Optional<RabbitListenerEndpointRegistry> registroRabbit() {
        return Optional.ofNullable(consumidoresRabbit.getIfAvailable());
    }
}
