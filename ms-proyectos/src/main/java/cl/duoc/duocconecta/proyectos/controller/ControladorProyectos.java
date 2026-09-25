package cl.duoc.duocconecta.proyectos.controller;

import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import cl.duoc.duocconecta.proyectos.dto.ProyectoDatos;
import cl.duoc.duocconecta.proyectos.dto.ProyectoRespuesta;
import cl.duoc.duocconecta.proyectos.service.ServicioProyectos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de la vitrina de proyectos de DuocConecta.
 *
 * <p>La identidad de quien pide siempre sale del token, nunca de un parámetro: así nadie puede
 * operar sobre el proyecto ajeno cambiando un valor de la petición.</p>
 */
@RestController
@RequestMapping("/api/v1/proyectos")
@Tag(name = "Proyectos", description = "Vitrina de proyectos de la comunidad Duoc UC")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorProyectos {

    /** Roles que pueden operar sobre la vitrina. */
    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    private final ServicioProyectos servicio;
    private final UsuarioActual usuarioActual;

    public ControladorProyectos(ServicioProyectos servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** Identificador de quien hace la petición, tomado del token. */
    private String autor() {
        return usuarioActual.obtener().oid();
    }

    /**
     * Publica un proyecto en la vitrina.
     *
     * @return 201 con el proyecto creado
     */
    @Operation(
            summary = "Publica un proyecto",
            description = "Quien lo publica queda como propietario: el único que después puede editarlo, ocultarlo o eliminarlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Proyecto publicado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @Content)
    })
    @PostMapping
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<ProyectoRespuesta> crear(@Valid @RequestBody ProyectoDatos dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicio.crear(dto, autor()));
    }

    /**
     * Lista los proyectos que la persona autenticada puede ver.
     *
     * <p>Son los públicos, los propios y aquellos compartidos donde figura como colaboradora.</p>
     */
    @Operation(
            summary = "Lista los proyectos visibles",
            description = "Los públicos, los propios —incluidos los ocultos— y los compartidos donde figura como colaboradora.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de proyectos visibles"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @Content)
    })
    @GetMapping
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<List<ProyectoRespuesta>> listar() {
        return ResponseEntity.ok(servicio.listarVisiblesPara(autor()));
    }

    /**
     * Devuelve un proyecto, si la persona tiene permiso para verlo.
     *
     * @return 200 con el proyecto; 403 si es privado o compartido y no le corresponde
     */
    @Operation(
            summary = "Obtiene un proyecto",
            description = "Si es privado o compartido y no le corresponde, responde 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyecto encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin acceso a este proyecto", content = @Content),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe", content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<ProyectoRespuesta> obtener(
            @Parameter(description = "Identificador del proyecto") @PathVariable UUID id) {
        return ResponseEntity.ok(servicio.obtenerSiVisible(id, autor()));
    }

    /**
     * Edita un proyecto propio, incluida su visibilidad.
     *
     * <p>Cambiar la visibilidad a privado es la forma de ocultarlo de la vitrina sin borrarlo.</p>
     */
    @Operation(
            summary = "Edita un proyecto propio",
            description = "Cambiar la visibilidad a PRIVADO lo saca de la vitrina sin borrarlo. Solo el propietario puede editar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyecto actualizado"),
            @ApiResponse(responseCode = "403", description = "Solo el propietario puede editarlo", content = @Content),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe", content = @Content)
    })
    @PutMapping("/{id}")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<ProyectoRespuesta> actualizar(@PathVariable UUID id,
                                                          @Valid @RequestBody ProyectoDatos dto) {
        return ResponseEntity.ok(servicio.actualizar(id, dto, autor()));
    }

    /**
     * Suma una persona como colaboradora de un proyecto compartido.
     */
    @Operation(
            summary = "Agrega una persona colaboradora",
            description = "Solo el propietario, y solo en proyectos de visibilidad COMPARTIDO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Colaboradora agregada"),
            @ApiResponse(responseCode = "403", description = "Solo el propietario, y solo en proyectos compartidos", content = @Content)
    })
    @PostMapping("/{id}/colaboradores")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<ProyectoRespuesta> agregarColaborador(
            @PathVariable UUID id, @RequestBody Map<String, String> cuerpo) {
        return ResponseEntity.ok(servicio.agregarColaborador(id, autor(), cuerpo.get("colaboradorId")));
    }

    /**
     * Elimina un proyecto propio. Para sacarlo de la vitrina sin perderlo, conviene ocultarlo.
     */
    @Operation(
            summary = "Elimina un proyecto propio",
            description = "Borra de forma definitiva. Para solo sacarlo de la vitrina conviene ocultarlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Proyecto eliminado"),
            @ApiResponse(responseCode = "403", description = "Solo el propietario puede eliminarlo", content = @Content)
    })
    @DeleteMapping("/{id}")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        servicio.eliminar(id, autor());
        return ResponseEntity.noContent().build();
    }
}
