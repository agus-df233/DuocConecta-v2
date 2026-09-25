package cl.duoc.duocconecta.proyectos.controller;

import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import cl.duoc.duocconecta.proyectos.dto.ComentarioDatos;
import cl.duoc.duocconecta.proyectos.dto.ComentarioRespuesta;
import cl.duoc.duocconecta.proyectos.service.ServicioComentarios;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comentarios de un proyecto.
 *
 * <p>Es la vía para dar retroalimentación sin pedir contacto: comentar es público entre quienes
 * ven el proyecto y no comparte teléfono, correo ni redes de nadie.</p>
 */
@RestController
@RequestMapping("/api/v1/proyectos/{proyectoId}/comentarios")
@Tag(name = "Comentarios", description = "Retroalimentación sobre los proyectos de la vitrina")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorComentarios {

    /** Roles que pueden comentar. */
    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    private final ServicioComentarios servicio;
    private final UsuarioActual usuarioActual;

    public ControladorComentarios(ServicioComentarios servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** Identificador de quien hace la petición, tomado del token. */
    private String autor() {
        return usuarioActual.obtener().oid();
    }

    /** Hilo completo del proyecto. */
    @Operation(
            summary = "Lista los comentarios de un proyecto",
            description = "El hilo completo, del más antiguo al más nuevo. Solo lo ve quien tiene acceso al proyecto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de comentarios"),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este proyecto", content = @Content),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe", content = @Content)
    })
    @GetMapping
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<List<ComentarioRespuesta>> listar(
            @Parameter(description = "Identificador del proyecto") @PathVariable UUID proyectoId) {
        return ResponseEntity.ok(servicio.listar(proyectoId, autor()));
    }

    /** Deja un comentario nuevo. */
    @Operation(
            summary = "Comenta un proyecto",
            description = "Comentar es retroalimentación entre quienes ya ven el proyecto: no comparte datos de contacto.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comentario publicado"),
            @ApiResponse(responseCode = "400", description = "El comentario venía vacío o es muy largo", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este proyecto", content = @Content),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe", content = @Content)
    })
    @PostMapping
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<ComentarioRespuesta> comentar(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody ComentarioDatos dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(proyectoId, dto, autor()));
    }
}
