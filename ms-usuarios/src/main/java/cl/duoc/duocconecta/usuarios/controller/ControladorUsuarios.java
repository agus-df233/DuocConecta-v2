package cl.duoc.duocconecta.usuarios.controller;

import cl.duoc.duocconecta.usuarios.dto.ActualizarPerfilDatos;
import cl.duoc.duocconecta.usuarios.dto.PerfilPrivadoRespuesta;
import cl.duoc.duocconecta.usuarios.dto.PerfilPublicoRespuesta;
import cl.duoc.duocconecta.usuarios.dto.RedesRespuesta;
import cl.duoc.duocconecta.usuarios.dto.VisibilidadRespuesta;
import cl.duoc.duocconecta.usuarios.service.ServicioUsuarios;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de perfiles de usuario de DuocConecta.
 *
 * <p>Todos los endpoints exigen un token válido de Azure AD. Los que operan sobre el perfil propio
 * ({@code /me}) exigen además un rol de la plataforma, que se deriva del dominio del correo: una
 * cuenta de un dominio no autorizado recibe 403 sin llegar a crear perfil.</p>
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios", description = "Perfiles, visibilidad y datos de contacto de la comunidad Duoc UC")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorUsuarios {

    /** Cantidad de perfiles por página cuando el cliente no pide otra cosa. */
    private static final int TAMANO_PAGINA_POR_DEFECTO = 20;

    /** Tope de perfiles por página, para que nadie pueda pedir la tabla entera de una vez. */
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    /** Roles que pueden operar sobre su propio perfil. */
    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    private final ServicioUsuarios servicio;

    public ControladorUsuarios(ServicioUsuarios servicio) {
        this.servicio = servicio;
    }

    /**
     * Devuelve el perfil de la persona autenticada y lo crea si es su primer ingreso.
     *
     * <p>Los datos iniciales (identificador, correo y nombre) se toman de los claims del token, no
     * de la petición. El rol se deduce del dominio del correo; si ese dominio no está autorizado,
     * el perfil no se crea y la respuesta es 403.</p>
     *
     * @return 200 con el perfil completo, incluidos los datos de contacto propios
     */
    @Operation(
            summary = "Obtiene o auto-provisiona el perfil propio",
            description = "Si es el primer ingreso lo crea con los claims del token y le asigna el rol según el dominio del correo. Incluye teléfono y redes, que nunca salen en las respuestas públicas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil obtenido o creado correctamente"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/me")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<PerfilPrivadoRespuesta> obtenerPerfilPropio() {
        return ResponseEntity.ok(servicio.obtenerOCrearPerfilPropio());
    }

    /**
     * Actualiza los datos editables del perfil propio.
     *
     * <p>Solo se pueden cambiar carrera, sede, biografía, teléfono y redes: son los datos que el
     * login no puede aprovisionar solo. El nombre, el correo, el rol y el identificador de Azure AD
     * vienen del token y no son modificables.</p>
     *
     * @param solicitud campos nuevos del perfil, ya validados
     * @return 200 con el perfil actualizado
     */
    @Operation(
            summary = "Actualiza el perfil propio",
            description = "Modifica carrera, sede, biografía, teléfono y redes. El nombre, el correo y el rol vienen del token y no se pueden cambiar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
            @ApiResponse(responseCode = "400", description = "Los datos enviados no pasaron la validación", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PutMapping("/me")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<PerfilPrivadoRespuesta> actualizarPerfilPropio(
            @Valid @RequestBody ActualizarPerfilDatos solicitud) {
        return ResponseEntity.ok(servicio.actualizarPerfilPropio(solicitud));
    }

    /**
     * Muestra u oculta el perfil propio en las búsquedas.
     *
     * <p>Es un interruptor: cada llamada deja la visibilidad en el estado contrario. Ocultarse no
     * borra nada, solo saca el perfil de los listados públicos.</p>
     *
     * @return 200 con el nuevo estado de visibilidad
     */
    @Operation(
            summary = "Alterna la visibilidad del perfil propio",
            description = "Ocultarse no borra nada: solo saca el perfil de las búsquedas y los listados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visibilidad actualizada"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PatchMapping("/me/visibilidad")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<VisibilidadRespuesta> alternarVisibilidad() {
        return ResponseEntity.ok(VisibilidadRespuesta.de(servicio.alternarVisibilidadPropia()));
    }

    /**
     * Devuelve las redes sociales de la persona autenticada.
     *
     * <p>Solo las propias. Las de otras personas son datos de contacto privados y quedan sujetas
     * al consentimiento mutuo, que se implementa en EP2.</p>
     *
     * @return 200 con el listado de redes, que puede venir vacío
     */
    @Operation(
            summary = "Devuelve las redes sociales del usuario autenticado",
            description = "Solo las propias: las de terceros son datos privados sujetos al consentimiento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de redes del usuario autenticado"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/me/redes")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<RedesRespuesta> obtenerRedesPropias() {
        return ResponseEntity.ok(new RedesRespuesta(servicio.obtenerRedesPropias()));
    }

    /**
     * Devuelve el perfil público de otra persona.
     *
     * <p>Nunca incluye teléfono ni redes. Si la persona ocultó su perfil, la respuesta es 404: se
     * responde igual que si no existiera, para no delatar su presencia en la plataforma.</p>
     *
     * @param id identificador del perfil
     * @return 200 con el perfil público
     */
    @Operation(
            summary = "Obtiene el perfil público de un usuario",
            description = "Nunca incluye teléfono ni redes. Si la persona se ocultó responde 404, para no revelar que está en la plataforma.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil público encontrado"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "El perfil no existe o está oculto", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<PerfilPublicoRespuesta> obtenerPerfilPublico(
            @Parameter(description = "Identificador del perfil a consultar")
            @PathVariable UUID id) {
        return ResponseEntity.ok(servicio.buscarPerfilPublico(id));
    }

    /**
     * Devuelve el perfil público a partir del identificador de Azure AD.
     *
     * <p>Los demás servicios guardan el {@code oid} del token, no el id interno de este
     * microservicio. Sin esta ruta, un proyecto o una solicitud solo pueden mostrar un
     * identificador opaco en vez del nombre de la persona.</p>
     *
     * @param oid identificador de la persona en Azure AD
     * @return 200 con el perfil público
     */
    @Operation(
            summary = "Obtiene el perfil público por identificador de Azure AD",
            description = "Igual que por id, pero con el oid del token. Es la que usa el BFF para traducir un identificador a un nombre.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil público encontrado"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "El perfil no existe o está oculto", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/por-oid/{oid}")
    public ResponseEntity<PerfilPublicoRespuesta> obtenerPerfilPublicoPorOid(
            @Parameter(description = "Identificador de la persona en Azure AD")
            @PathVariable String oid) {
        return ResponseEntity.ok(servicio.buscarPerfilPublicoPorOid(oid));
    }

    /**
     * Lista los perfiles visibles de la comunidad, con filtros opcionales.
     *
     * <p>Es lo que alimenta la vitrina. Solo aparecen los perfiles marcados como visibles y sin
     * datos de contacto.</p>
     *
     * @param carrera filtro opcional por carrera
     * @param sede    filtro opcional por sede
     * @param pagina  número de página, empezando en cero
     * @param tamano  cantidad de perfiles por página
     * @return 200 con el listado de perfiles públicos
     */
    @Operation(
            summary = "Lista los perfiles públicos visibles",
            description = "Paginado, con filtros opcionales por carrera y sede. Nunca incluye teléfono ni redes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de perfiles visibles"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping
    public ResponseEntity<List<PerfilPublicoRespuesta>> listarPerfiles(
            @Parameter(description = "Filtra por carrera. Si se omite, no filtra.", example = "Ingeniería en Informática")
            @RequestParam(required = false) String carrera,

            @Parameter(description = "Filtra por sede. Si se omite, no filtra.", example = "Plaza Oeste")
            @RequestParam(required = false) String sede,

            @Parameter(description = "Número de página, empezando en cero")
            @RequestParam(defaultValue = "0") int pagina,

            @Parameter(description = "Cantidad de perfiles por página (máximo 100)")
            @RequestParam(defaultValue = "20") int tamano) {

        Pageable paginacion = PageRequest.of(Math.max(pagina, 0), acotarTamano(tamano));
        Page<PerfilPublicoRespuesta> resultado = servicio.listarPerfilesVisibles(carrera, sede, paginacion);

        return ResponseEntity.ok(resultado.getContent());
    }

    /**
     * Deja el tamaño de página dentro de un rango razonable, para que una petición no pueda
     * pedir la tabla completa.
     */
    private static int acotarTamano(int tamanoPedido) {
        if (tamanoPedido <= 0) {
            return TAMANO_PAGINA_POR_DEFECTO;
        }
        return Math.min(tamanoPedido, TAMANO_PAGINA_MAXIMO);
    }
}
