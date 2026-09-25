package cl.duoc.duocconecta.notificaciones.controller;

import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
import cl.duoc.duocconecta.notificaciones.dto.EstadoCola;
import cl.duoc.duocconecta.notificaciones.dto.EstadoConsumidor;
import cl.duoc.duocconecta.notificaciones.dto.MensajeDescartado;
import cl.duoc.duocconecta.notificaciones.service.MonitorDescartes;
import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de la mensajería de este servicio, solo para ADMINISTRADOR: estado de las DLQ y
 * el interruptor de los consumidores, que sirve para demostrar el desacoplamiento en Fargate
 * sin detener la tarea.
 */
@RestController
@RequestMapping("/api/v1/notificaciones/admin")
@PreAuthorize(ReglasDeAcceso.SOLO_ADMINISTRADOR)
@Tag(name = "Administración de notificaciones", description = "DLQ y consumidores. Solo ADMINISTRADOR")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorAdministracionNotificaciones {

    private static final Logger log = LoggerFactory.getLogger(ControladorAdministracionNotificaciones.class);

    private final MonitorDescartes monitor;
    private final MessageListenerContainerRegistry consumidores;

    public ControladorAdministracionNotificaciones(MonitorDescartes monitor,
                                                   MessageListenerContainerRegistry consumidores) {
        this.monitor = monitor;
        this.consumidores = consumidores;
    }

    /** Cuántos mensajes hay en cada DLQ. */
    @Operation(summary = "Estado de las colas de descarte")
    @GetMapping("/descartes")
    public List<EstadoCola> descartes() {
        return monitor.estado();
    }

    /** Hasta 10 mensajes de una DLQ, sin sacarlos de ahí. */
    @Operation(summary = "Mira los mensajes de una DLQ sin consumirlos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Muestra de mensajes"),
            @ApiResponse(responseCode = "404", description = "No es una DLQ de este servicio", content = @Content)
    })
    @GetMapping("/descartes/{cola}/mensajes")
    public List<MensajeDescartado> mensajes(@PathVariable String cola) {
        return monitor.muestra(cola);
    }

    @Operation(summary = "Estado de los consumidores")
    @GetMapping("/consumidores")
    public List<EstadoConsumidor> estadoConsumidores() {
        return consumidores.getListenerContainers().stream()
                .map(c -> new EstadoConsumidor(c.getId(), c.isRunning()))
                .sorted(Comparator.comparing(EstadoConsumidor::id))
                .toList();
    }

    /** Deja de consumir: los mensajes se acumulan en las colas, nada se pierde. */
    @Operation(summary = "Pausa los consumidores", description = "Los eventos siguen llegando y esperan en las colas.")
    @PostMapping("/consumidores/pausa")
    public List<EstadoConsumidor> pausar() {
        consumidores.getListenerContainers().forEach(MessageListenerContainer::stop);
        log.warn("Consumidores pausados por un administrador");
        return estadoConsumidores();
    }

    /** Vuelve a consumir: lo acumulado se procesa solo. */
    @Operation(summary = "Reanuda los consumidores")
    @PostMapping("/consumidores/reanudar")
    public List<EstadoConsumidor> reanudar() {
        consumidores.getListenerContainers().forEach(MessageListenerContainer::start);
        log.info("Consumidores reanudados por un administrador");
        return estadoConsumidores();
    }
}
