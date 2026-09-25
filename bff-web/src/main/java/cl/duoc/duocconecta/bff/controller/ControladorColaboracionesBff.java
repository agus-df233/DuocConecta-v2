package cl.duoc.duocconecta.bff.controller;

import cl.duoc.duocconecta.bff.dto.ColaboracionRespuesta;
import cl.duoc.duocconecta.bff.dto.PerfilUsuario;
import cl.duoc.duocconecta.bff.dto.DecisionColaboracion;
import cl.duoc.duocconecta.bff.dto.SolicitudColaboracionDatos;
import cl.duoc.duocconecta.bff.service.ClienteContacto;
import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import cl.duoc.duocconecta.bff.dto.DatosDeContacto;
import cl.duoc.duocconecta.bff.service.ResolvedorAutores;
import cl.duoc.duocconecta.bff.service.ResolvedorProyectos;
import cl.duoc.duocconecta.bff.service.ClienteUsuarios;
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
 * Solicitudes de colaboración, vistas desde el frontend.
 *
 * <p>Existe además del microservicio por una razón concreta: al aceptar, los datos de contacto se
 * arman solos con el perfil de quien acepta, en vez de pedirle que los escriba a mano. Componer
 * datos de ms-usuarios y ms-contacto es tarea del BFF, que es la única capa que puede hablar con
 * los dos sin que ninguno lea la base del otro.</p>
 */
@RestController
@RequestMapping("/api/v1/bff/colaboraciones")
@Tag(name = "BFF · Colaboraciones", description = "Solicitudes de contacto con los datos ya compuestos")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorColaboracionesBff {

    /** Roles que pueden pedir y responder colaboraciones. */
    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    /** El campo de la base admite 500 caracteres; se recorta antes de enviarlo. */
    private static final int LARGO_MAXIMO_DATOS = 500;

    private final ClienteContacto clienteContacto;
    private final ClienteUsuarios clienteUsuarios;
    private final ResolvedorAutores resolvedorAutores;
    private final ResolvedorProyectos resolvedorProyectos;
    private final UsuarioActual usuarioActual;

    public ControladorColaboracionesBff(ClienteContacto clienteContacto, ClienteUsuarios clienteUsuarios,
                                       ResolvedorAutores resolvedorAutores,
                                       ResolvedorProyectos resolvedorProyectos,
                                       UsuarioActual usuarioActual) {
        this.clienteContacto = clienteContacto;
        this.clienteUsuarios = clienteUsuarios;
        this.resolvedorAutores = resolvedorAutores;
        this.resolvedorProyectos = resolvedorProyectos;
        this.usuarioActual = usuarioActual;
    }

    /** Envía una solicitud de colaboración a otra persona. */
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
    public ResponseEntity<ColaboracionRespuesta> solicitar(
            @Valid @RequestBody SolicitudColaboracionDatos solicitud) {
        // Pedir contacto es ofrecer el propio: se adjuntan los datos de quien pide. No se muestran
        // a nadie mientras la solicitud siga pendiente.
        DatosDeContacto mios = datosDeContactoPropios(solicitud.compartirTelefono());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(uno(clienteContacto.crear(solicitud, mios)));
    }

    /**
     * Acepta o rechaza una solicitud recibida.
     *
     * <p>Al aceptar, el BFF lee el perfil propio y arma con él los datos que se comparten, así la
     * persona no tiene que escribirlos. Al rechazar no se envía nada.</p>
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
    public ResponseEntity<ColaboracionRespuesta> responder(
            @Parameter(description = "Identificador de la solicitud") @PathVariable UUID id,
            @RequestBody DecisionColaboracion respuesta) {

        DatosDeContacto contacto = respuesta.aceptar()
                ? datosDeContactoPropios(respuesta.compartirTelefono())
                : DatosDeContacto.NINGUNO;

        return ResponseEntity.ok(uno(clienteContacto.responder(id, respuesta.aceptar(), contacto)));
    }

    /** Solicitudes que otras personas me enviaron. */
    @Operation(
            summary = "Lista las solicitudes recibidas",
            description = "Bandeja de entrada: lo que otras personas pidieron, con su estado.")
    @ApiResponse(responseCode = "200", description = "Listado de solicitudes recibidas")
    @GetMapping("/recibidas")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<List<ColaboracionRespuesta>> recibidas() {
        return ResponseEntity.ok(conPersonas(clienteContacto.recibidas()));
    }

    /** Solicitudes que envié, con los datos de contacto de las que ya fueron aceptadas. */
    @Operation(
            summary = "Lista las solicitudes enviadas",
            description = "En las aceptadas vienen el correo, el teléfono y las redes que la otra persona decidió compartir.")
    @ApiResponse(responseCode = "200", description = "Listado de solicitudes enviadas")
    @GetMapping("/enviadas")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<List<ColaboracionRespuesta>> enviadas() {
        return ResponseEntity.ok(conPersonas(clienteContacto.enviadas()));
    }

    /**
     * Reemplaza los identificadores por personas antes de devolver el listado.
     *
     * <p>ms-contacto guarda el oid de cada parte y nada más. Sin esta traducción la bandeja diría
     * "00000000-0000… quiere compartir contacto contigo".</p>
     */
    private List<ColaboracionRespuesta> conPersonas(List<ColaboracionRespuesta> solicitudes) {
        ResolvedorAutores.Consulta personas = resolvedorAutores.abrir();
        ResolvedorProyectos.Consulta proyectos = resolvedorProyectos.abrir();
        String yo = usuarioActual.obtener().oid();
        return solicitudes.stream()
                .map(s -> s.resuelta(personas.autorDe(s.solicitanteId()),
                        personas.autorDe(s.solicitadoId()),
                        proyectos.nombreDe(s.proyectoId()), yo))
                .toList();
    }

    /** Lo mismo para una sola solicitud, la recién creada o la recién respondida. */
    private ColaboracionRespuesta uno(ColaboracionRespuesta solicitud) {
        return conPersonas(List.of(solicitud)).get(0);
    }

    /**
     * Arma los datos de contacto con el perfil de quien está aceptando.
     *
     * <p>El correo institucional va siempre porque es el mínimo para poder escribirse. El teléfono
     * es opcional y se suma solo si la persona lo pidió: es el dato más sensible de todos.</p>
     */
    private DatosDeContacto datosDeContactoPropios(boolean incluirTelefono) {
        PerfilUsuario perfil = clienteUsuarios.obtenerPerfilPropio();
        List<String> redes = clienteUsuarios.obtenerRedesPropias();

        return new DatosDeContacto(
                perfil.correo(),
                incluirTelefono ? perfil.telefono() : null,
                (redes == null || redes.isEmpty()) ? null : String.join(", ", redes));
    }
}
