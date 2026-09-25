package cl.duoc.duocconecta.bff.controller;

import cl.duoc.duocconecta.bff.dto.ComentarioDatos;
import cl.duoc.duocconecta.bff.dto.ComentarioRespuesta;
import cl.duoc.duocconecta.bff.dto.ProyectoRespuesta;
import cl.duoc.duocconecta.bff.service.ClienteProyectos;
import cl.duoc.duocconecta.bff.service.ResolvedorAutores;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La vitrina, ya lista para pintar en pantalla.
 *
 * <p>Es el listado de ms-proyectos con los datos de quien publicó cada uno resueltos contra
 * ms-usuarios: nombre, carrera y sede. Solo lectura: publicar, editar y ocultar van directo al microservicio, porque ahí
 * no hay nada que componer y hacerlo pasar por el BFF sería una capa de más.</p>
 */
@RestController
@RequestMapping("/api/v1/bff/vitrina")
@Tag(name = "BFF · Vitrina", description = "Proyectos con los datos de quien los publicó")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorVitrinaBff {

    /** Roles que pueden ver la vitrina. */
    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    private final ClienteProyectos clienteProyectos;
    private final ResolvedorAutores resolvedorAutores;

    public ControladorVitrinaBff(ClienteProyectos clienteProyectos, ResolvedorAutores resolvedorAutores) {
        this.clienteProyectos = clienteProyectos;
        this.resolvedorAutores = resolvedorAutores;
    }

    /**
     * Devuelve los proyectos visibles con el nombre de cada propietario.
     *
     * @return 200 con el listado
     */
    @Operation(
            summary = "Lista los proyectos de la vitrina",
            description = "Los públicos, los propios y los compartidos, con el nombre, la carrera y la sede de quien publicó cada uno. Si esa persona ocultó su perfil, esos datos llegan en null.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de proyectos"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @Content)
    })
    @GetMapping
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<List<ProyectoRespuesta>> listar() {
        ResolvedorAutores.Consulta consulta = resolvedorAutores.abrir();
        return ResponseEntity.ok(clienteProyectos.listar().stream()
                .map(p -> p.conAutor(consulta.autorDe(p.propietarioId())))
                .toList());
    }

    /**
     * Hilo de comentarios de un proyecto, con el nombre de cada persona.
     *
     * @return 200 con los comentarios del más antiguo al más nuevo
     */
    @Operation(
            summary = "Lista los comentarios de un proyecto",
            description = "El hilo completo, del más antiguo al más nuevo. Solo lo ve quien tiene acceso al proyecto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de comentarios"),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este proyecto", content = @Content)
    })
    @GetMapping("/{proyectoId}/comentarios")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<List<ComentarioRespuesta>> comentarios(@PathVariable UUID proyectoId) {
        ResolvedorAutores.Consulta consulta = resolvedorAutores.abrir();
        return ResponseEntity.ok(clienteProyectos.comentarios(proyectoId).stream()
                .map(c -> c.conAutor(consulta.autorDe(c.autorId())))
                .toList());
    }

    /** Publica un comentario y lo devuelve ya con el nombre de quien lo escribió. */
    @Operation(
            summary = "Comenta un proyecto",
            description = "Comentar es retroalimentación entre quienes ya ven el proyecto: no comparte datos de contacto.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comentario publicado"),
            @ApiResponse(responseCode = "400", description = "El comentario venía vacío o es muy largo", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este proyecto", content = @Content)
    })
    @PostMapping("/{proyectoId}/comentarios")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<ComentarioRespuesta> comentar(@PathVariable UUID proyectoId,
                                                       @Valid @RequestBody ComentarioDatos comentario) {
        ComentarioRespuesta creado = clienteProyectos.comentar(proyectoId, comentario);
        ResolvedorAutores.Consulta consulta = resolvedorAutores.abrir();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creado.conAutor(consulta.autorDe(creado.autorId())));
    }
}
