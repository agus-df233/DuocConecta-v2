package cl.duoc.duocconecta.contacto.controller;

import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import cl.duoc.duocconecta.contacto.dto.DecisionSolicitud;
import cl.duoc.duocconecta.contacto.dto.SolicitudDatos;
import cl.duoc.duocconecta.contacto.dto.SolicitudRespuesta;
import cl.duoc.duocconecta.contacto.service.ServicioSolicitudes;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de las solicitudes de colaboración.
 *
 * <p>Es el mecanismo de consentimiento de la plataforma: nadie ve los datos de contacto de otra
 * persona hasta que esa persona acepta explícitamente una solicitud y elige qué compartir.</p>
 */
@RestController
@RequestMapping("/api/v1/colaboraciones")
@Tag(name = "Colaboraciones", description = "Solicitudes de contacto con consentimiento")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorSolicitudes {

    /** Roles que pueden pedir y responder colaboraciones. */
    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    private final ServicioSolicitudes servicio;
    private final UsuarioActual usuarioActual;

    public ControladorSolicitudes(ServicioSolicitudes servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** Identificador de quien hace la petición, tomado del token. */
    private String yo() {
        return usuarioActual.obtener().oid();
    }

    /**
     * Envía una solicitud de colaboración a otra persona.
     *
     * @return 201 con la solicitud en estado PENDIENTE
     */
    @Operation(
            summary = "Envía una solicitud de colaboración",
            description = "Queda PENDIENTE hasta que la otra parte responda. No se comparte ningún dato todavía.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitud enviada"),
            @ApiResponse(responseCode = "400", description = "Solicitud a uno mismo", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya hay una solicitud pendiente a esa persona", content = @Content),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @Content)
    })
    @PostMapping
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<SolicitudRespuesta> crear(@Valid @RequestBody SolicitudDatos dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(dto, yo()));
    }

    /**
     * Acepta o rechaza una solicitud recibida.
     *
     * <p>Aceptar es lo único que habilita el intercambio de datos de contacto, y quien acepta
     * decide qué comparte.</p>
     */
    @Operation(
            summary = "Acepta o rechaza una solicitud recibida",
            description = "Solo responde la persona destinataria, y una sola vez. Al aceptar se comparten sus datos de contacto; al rechazar, ninguno.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud respondida"),
            @ApiResponse(responseCode = "403", description = "No eres el destinatario", content = @Content),
            @ApiResponse(responseCode = "409", description = "La solicitud ya fue respondida", content = @Content),
            @ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
    })
    @PatchMapping("/{id}/responder")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<SolicitudRespuesta> responder(
            @Parameter(description = "Identificador de la solicitud") @PathVariable UUID id,
            @Valid @RequestBody DecisionSolicitud respuesta) {
        return ResponseEntity.ok(servicio.responder(id, yo(), respuesta));
    }

    /** Solicitudes que otras personas me enviaron. */
    @Operation(
            summary = "Lista las solicitudes recibidas",
            description = "La bandeja desde la que se acepta o rechaza.")
    @ApiResponse(responseCode = "200", description = "Listado de solicitudes recibidas")
    @GetMapping("/recibidas")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<List<SolicitudRespuesta>> recibidas() {
        return ResponseEntity.ok(servicio.recibidas(yo()));
    }

    /** Solicitudes que envié, con su estado y los datos que me compartieron al aceptar. */
    @Operation(
            summary = "Lista las solicitudes enviadas",
            description = "En las aceptadas vienen el correo, el teléfono y las redes que la otra persona decidió compartir.")
    @ApiResponse(responseCode = "200", description = "Listado de solicitudes enviadas")
    @GetMapping("/enviadas")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<List<SolicitudRespuesta>> enviadas() {
        return ResponseEntity.ok(servicio.enviadas(yo()));
    }
}
