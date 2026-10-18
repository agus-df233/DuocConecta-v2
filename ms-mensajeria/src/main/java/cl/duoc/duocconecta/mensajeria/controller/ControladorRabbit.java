package cl.duoc.duocconecta.mensajeria.controller;

import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.BindingDatos;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.ColaRabbitRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.CrearColaRabbitDatos;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.CrearExchangeDatos;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.ExchangeRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.PurgaRabbitRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.rabbit.RedriveRabbitRespuesta;
import cl.duoc.duocconecta.mensajeria.service.ServicioAdministracionRabbit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Colas, exchanges, bindings y redrive de RabbitMQ (prefijo {@code duocconecta.}). Solo ADMINISTRADOR. */
@RestController
@RequestMapping("/api/v1/mensajeria/rabbit")
@PreAuthorize(ReglasDeAcceso.SOLO_ADMINISTRADOR)
@ConditionalOnProperty(prefix = "duocconecta.mensajeria.rabbit", name = "habilitada", havingValue = "true")
@Tag(name = "RabbitMQ", description = "Colas, exchanges, bindings, purga y redrive de RabbitMQ")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorRabbit {

    private final ServicioAdministracionRabbit servicio;

    public ControladorRabbit(ServicioAdministracionRabbit servicio) {
        this.servicio = servicio;
    }

    /** Las colas de la topología base, con sus DLQ y contadores. */
    @Operation(summary = "Lista las colas de RabbitMQ", description = "Las de la topología base: AMQP no permite listar todas las del broker.")
    @GetMapping("/colas")
    public List<ColaRabbitRespuesta> listarColas() {
        return servicio.listarColas();
    }

    /** Contadores de una cola: mensajes listos y consumidores conectados. */
    @Operation(summary = "Detalle de una cola de RabbitMQ")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle"),
            @ApiResponse(responseCode = "404", description = "La cola no existe", content = @Content)
    })
    @GetMapping("/colas/{nombre}")
    public ColaRabbitRespuesta detalleCola(@PathVariable String nombre) {
        return servicio.detalleCola(nombre);
    }

    /** Crea una cola quorum y, por defecto, su DLQ enlazada al dead-letter exchange. */
    @Operation(summary = "Crea una cola", description = "Por defecto crea también su DLQ <nombre>.descartes, enlazada al DLX.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cola creada"),
            @ApiResponse(responseCode = "400", description = "Nombre o parámetros inválidos", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe", content = @Content)
    })
    @PostMapping("/colas")
    public ResponseEntity<ColaRabbitRespuesta> crearCola(@Valid @RequestBody CrearColaRabbitDatos datos) {
        ColaRabbitRespuesta creada = servicio.crearCola(datos);
        return ResponseEntity.created(URI.create("/api/v1/mensajeria/rabbit/colas/" + creada.nombre())).body(creada);
    }

    /** Elimina una cola. Las de la topología base están protegidas. */
    @Operation(summary = "Elimina una cola")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminada"),
            @ApiResponse(responseCode = "404", description = "No existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Es parte de la topología base", content = @Content)
    })
    @DeleteMapping("/colas/{nombre}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String nombre) {
        servicio.eliminarCola(nombre);
        return ResponseEntity.noContent().build();
    }

    /** Vacía una cola; lo normal es limpiar una DLQ ya revisada. */
    @Operation(summary = "Purga una cola de RabbitMQ")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purgada, con la cantidad de mensajes borrados"),
            @ApiResponse(responseCode = "400", description = "No es una cola de la plataforma", content = @Content),
            @ApiResponse(responseCode = "404", description = "No existe", content = @Content)
    })
    @PostMapping("/colas/{nombre}/purga")
    public PurgaRabbitRespuesta purgarCola(@PathVariable String nombre) {
        return servicio.purgarCola(nombre);
    }

    /** Devuelve los mensajes de una DLQ a su cola de origen. */
    @Operation(summary = "Redrive: devuelve los mensajes de una DLQ a su cola",
            description = "Solo desde una cola <cola>.descartes. Mueve hasta 100 mensajes por llamada.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Mensajes devueltos"),
            @ApiResponse(responseCode = "400", description = "No es una DLQ de la plataforma", content = @Content),
            @ApiResponse(responseCode = "404", description = "La DLQ o su cola no existen", content = @Content)
    })
    @PostMapping("/colas/{nombre}/redrive")
    public ResponseEntity<RedriveRabbitRespuesta> redrive(@PathVariable String nombre) {
        return ResponseEntity.accepted().body(servicio.redrive(nombre));
    }

    /** Crea un exchange direct, topic o fanout. */
    @Operation(summary = "Crea un exchange")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Exchange creado"),
            @ApiResponse(responseCode = "400", description = "Nombre o tipo inválido", content = @Content),
            @ApiResponse(responseCode = "409", description = "Es parte de la topología base", content = @Content)
    })
    @PostMapping("/exchanges")
    public ResponseEntity<ExchangeRespuesta> crearExchange(@Valid @RequestBody CrearExchangeDatos datos) {
        ExchangeRespuesta creado = servicio.crearExchange(datos);
        return ResponseEntity.created(URI.create("/api/v1/mensajeria/rabbit/exchanges/" + creado.nombre())).body(creado);
    }

    /** Elimina un exchange. Los de la topología base están protegidos. */
    @Operation(summary = "Elimina un exchange")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminado"),
            @ApiResponse(responseCode = "409", description = "Es parte de la topología base", content = @Content)
    })
    @DeleteMapping("/exchanges/{nombre}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String nombre) {
        servicio.eliminarExchange(nombre);
        return ResponseEntity.noContent().build();
    }

    /** Une una cola a un exchange con una routing key o un patrón. */
    @Operation(summary = "Crea un binding")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Binding creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
            @ApiResponse(responseCode = "404", description = "La cola no existe", content = @Content)
    })
    @PostMapping("/bindings")
    public ResponseEntity<BindingDatos> crearBinding(@Valid @RequestBody BindingDatos datos) {
        return ResponseEntity.status(201).body(servicio.crearBinding(datos));
    }

    /** Quita un binding. Los de la topología base están protegidos. */
    @Operation(summary = "Elimina un binding")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminado"),
            @ApiResponse(responseCode = "409", description = "Es parte de la topología base", content = @Content)
    })
    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@Valid @RequestBody BindingDatos datos) {
        servicio.eliminarBinding(datos);
        return ResponseEntity.noContent().build();
    }
}
