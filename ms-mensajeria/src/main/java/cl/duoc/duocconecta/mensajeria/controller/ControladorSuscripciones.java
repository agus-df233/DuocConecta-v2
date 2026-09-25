package cl.duoc.duocconecta.mensajeria.controller;

import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
import cl.duoc.duocconecta.mensajeria.dto.CrearSuscripcionDatos;
import cl.duoc.duocconecta.mensajeria.dto.SuscripcionRespuesta;
import cl.duoc.duocconecta.mensajeria.service.ServicioAdministracionColas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Suscripciones de cola a tópico, el equivalente de los bindings. Solo ADMINISTRADOR. */
@RestController
@RequestMapping("/api/v1/mensajeria/suscripciones")
@PreAuthorize(ReglasDeAcceso.SOLO_ADMINISTRADOR)
@Tag(name = "Suscripciones (bindings)", description = "Unir colas a tópicos con filtro por prefijo de tipo")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorSuscripciones {

    private final ServicioAdministracionColas servicio;

    public ControladorSuscripciones(ServicioAdministracionColas servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Lista las suscripciones")
    @GetMapping
    public List<SuscripcionRespuesta> listar(
            @Parameter(description = "Tópico; sin él, las de todos") @RequestParam(required = false) String topico) {
        return servicio.listarSuscripciones(topico);
    }

    @Operation(summary = "Suscribe una cola a un tópico",
            description = "Con entrega cruda y DLQ; la política de la cola se agrega sola.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Suscripción creada"),
            @ApiResponse(responseCode = "400", description = "Nombres o filtro inválidos", content = @Content),
            @ApiResponse(responseCode = "404", description = "El tópico o la cola no existen", content = @Content),
            @ApiResponse(responseCode = "409", description = "La cola ya está suscrita", content = @Content)
    })
    @PostMapping
    public ResponseEntity<SuscripcionRespuesta> crear(@Valid @RequestBody CrearSuscripcionDatos datos) {
        SuscripcionRespuesta creada = servicio.crearSuscripcion(datos);
        return ResponseEntity.created(URI.create("/api/v1/mensajeria/suscripciones/" + creada.id())).body(creada);
    }

    @Operation(summary = "Elimina una suscripción", description = "Las de la topología base están protegidas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminada"),
            @ApiResponse(responseCode = "404", description = "No existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Es parte de la topología base", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        servicio.eliminarSuscripcion(id);
        return ResponseEntity.noContent().build();
    }
}
