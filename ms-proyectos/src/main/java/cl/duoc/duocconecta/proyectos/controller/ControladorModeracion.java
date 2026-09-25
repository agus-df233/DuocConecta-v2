package cl.duoc.duocconecta.proyectos.controller;

import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import cl.duoc.duocconecta.proyectos.domain.Visibilidad;
import cl.duoc.duocconecta.proyectos.dto.ComentarioRespuesta;
import cl.duoc.duocconecta.proyectos.dto.OcultarProyectoDatos;
import cl.duoc.duocconecta.proyectos.dto.ProyectoRespuesta;
import cl.duoc.duocconecta.proyectos.service.ServicioModeracion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moderación de la vitrina, solo para el rol ADMINISTRADOR.
 *
 * <p>Vive bajo {@code /api/v1/proyectos/admin} para que las rutas actuales del ALB y del API
 * Gateway lo cubran. Spring prefiere el literal {@code admin} antes que {@code {id}}.</p>
 */
@RestController
@RequestMapping("/api/v1/proyectos/admin")
@PreAuthorize(ReglasDeAcceso.SOLO_ADMINISTRADOR)
@Tag(name = "Moderación", description = "Ocultar, mostrar y borrar proyectos; borrar comentarios. Solo ADMINISTRADOR")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorModeracion {

    private final ServicioModeracion servicio;
    private final UsuarioActual usuarioActual;

    public ControladorModeracion(ServicioModeracion servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** Identificador del administrador, tomado del token. */
    private String administrador() {
        return usuarioActual.obtener().oid();
    }

    /** Todos los proyectos, incluidos privados, compartidos y ocultos. */
    @Operation(summary = "Lista todos los proyectos", description = "Filtros opcionales por marca de moderación y por visibilidad.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR", content = @Content)
    })
    @GetMapping
    public List<ProyectoRespuesta> listar(
            @Parameter(description = "true: solo ocultos · false: solo visibles") @RequestParam(required = false) Boolean oculto,
            @Parameter(description = "PUBLICO, COMPARTIDO o PRIVADO") @RequestParam(required = false) Visibilidad visibilidad) {
        return servicio.listar(oculto, visibilidad);
    }

    /** Saca el proyecto de la vitrina sin borrarlo. La dueña lo sigue viendo, con el motivo. */
    @Operation(summary = "Oculta un proyecto", description = "La dueña recibe un aviso con el motivo y no puede revertirlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyecto oculto"),
            @ApiResponse(responseCode = "400", description = "Falta el motivo o es muy largo", content = @Content),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya estaba oculto", content = @Content)
    })
    @PatchMapping("/{id}/ocultar")
    public ProyectoRespuesta ocultar(@PathVariable UUID id, @Valid @RequestBody OcultarProyectoDatos datos) {
        return servicio.ocultar(id, datos.motivo(), administrador());
    }

    /** Devuelve a la vitrina un proyecto oculto. */
    @Operation(summary = "Vuelve a mostrar un proyecto oculto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyecto visible otra vez"),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "No estaba oculto", content = @Content)
    })
    @PatchMapping("/{id}/mostrar")
    public ProyectoRespuesta mostrar(@PathVariable UUID id) {
        return servicio.mostrar(id, administrador());
    }

    /** Borra cualquier proyecto, con sus archivos en S3. */
    @Operation(summary = "Elimina un proyecto", description = "Sin la regla de dueño. La dueña recibe un aviso.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminado"),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        servicio.eliminar(id, administrador());
        return ResponseEntity.noContent().build();
    }

    /** Los comentarios de un proyecto, aunque sea privado u oculto; sin proyecto, los más recientes. */
    @Operation(summary = "Lista comentarios para moderar")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado")})
    @GetMapping("/comentarios")
    public List<ComentarioRespuesta> comentarios(
            @Parameter(description = "Proyecto; sin él, los 50 más recientes") @RequestParam(required = false) UUID proyectoId) {
        return servicio.comentarios(proyectoId);
    }

    /** Borra un comentario. Quien lo escribió recibe un aviso. */
    @Operation(summary = "Elimina un comentario")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminado"),
            @ApiResponse(responseCode = "404", description = "El comentario no existe", content = @Content)
    })
    @DeleteMapping("/comentarios/{id}")
    public ResponseEntity<Void> eliminarComentario(@PathVariable UUID id) {
        servicio.eliminarComentario(id, administrador());
        return ResponseEntity.noContent().build();
    }
}
