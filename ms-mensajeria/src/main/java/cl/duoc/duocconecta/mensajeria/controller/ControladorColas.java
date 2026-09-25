package cl.duoc.duocconecta.mensajeria.controller;

import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
import cl.duoc.duocconecta.mensajeria.dto.ColaRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.CrearColaDatos;
import cl.duoc.duocconecta.mensajeria.dto.RedriveRespuesta;
import cl.duoc.duocconecta.mensajeria.service.ServicioAdministracionColas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Colas SQS de la plataforma (prefijo {@code duocconecta-}). Solo ADMINISTRADOR. */
@RestController
@RequestMapping("/api/v1/mensajeria/colas")
@PreAuthorize(ReglasDeAcceso.SOLO_ADMINISTRADOR)
@Tag(name = "Colas", description = "Crear, listar, purgar y eliminar colas SQS, y devolver mensajes desde una DLQ")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorColas {

    private final ServicioAdministracionColas servicio;

    public ControladorColas(ServicioAdministracionColas servicio) {
        this.servicio = servicio;
    }

    /** Todas las colas de la plataforma, con sus contadores. */
    @Operation(summary = "Lista las colas")
    @GetMapping
    public List<ColaRespuesta> listar() {
        return servicio.listarColas();
    }

    /** Una cola: contadores, retención, visibilidad y su DLQ. */
    @Operation(summary = "Detalle de una cola")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle"),
            @ApiResponse(responseCode = "404", description = "La cola no existe", content = @Content)
    })
    @GetMapping("/{nombre}")
    public ColaRespuesta detalle(@PathVariable String nombre) {
        return servicio.detalleCola(nombre);
    }

    /** Crea una cola y, por defecto, su DLQ {@code <nombre>-descartes} con la redrive. */
    @Operation(summary = "Crea una cola", description = "Por defecto crea también su DLQ <nombre>-descartes con la redrive.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cola creada"),
            @ApiResponse(responseCode = "400", description = "Nombre o parámetros inválidos", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ColaRespuesta> crear(@Valid @RequestBody CrearColaDatos datos) {
        ColaRespuesta creada = servicio.crearCola(datos);
        return ResponseEntity.created(URI.create("/api/v1/mensajeria/colas/" + creada.nombre())).body(creada);
    }

    /** Elimina una cola. Las de la topología base están protegidas. */
    @Operation(summary = "Elimina una cola", description = "SQS tarda hasta 60 s en liberar el nombre.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminada"),
            @ApiResponse(responseCode = "404", description = "La cola no existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Es parte de la topología base", content = @Content)
    })
    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(@PathVariable String nombre) {
        servicio.eliminarCola(nombre);
        return ResponseEntity.noContent().build();
    }

    /** Borra todos los mensajes de la cola. */
    @Operation(summary = "Purga una cola", description = "SQS permite una purga por minuto.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Purga iniciada"),
            @ApiResponse(responseCode = "409", description = "Ya hay una purga en curso", content = @Content)
    })
    @PostMapping("/{nombre}/purga")
    public ResponseEntity<Void> purgar(@PathVariable String nombre) {
        servicio.purgarCola(nombre);
        return ResponseEntity.accepted().build();
    }

    /** Devuelve los mensajes de una DLQ a su cola de origen. */
    @Operation(summary = "Redrive: devuelve los mensajes de una DLQ a su cola")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Tarea iniciada"),
            @ApiResponse(responseCode = "409", description = "No es la DLQ de ninguna cola", content = @Content)
    })
    @PostMapping("/{nombre}/redrive")
    public ResponseEntity<RedriveRespuesta> redrive(@PathVariable String nombre) {
        return ResponseEntity.accepted().body(servicio.redrive(nombre));
    }
}
