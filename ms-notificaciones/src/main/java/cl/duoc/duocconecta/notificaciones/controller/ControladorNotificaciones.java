package cl.duoc.duocconecta.notificaciones.controller;

import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import cl.duoc.duocconecta.notificaciones.dto.AvisoRespuesta;
import cl.duoc.duocconecta.notificaciones.service.ServicioAvisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** La bandeja de avisos de la persona autenticada. El {@code oid} sale siempre del token. */
@RestController
@RequestMapping("/api/v1/notificaciones")
@PreAuthorize(ReglasDeAcceso.ROLES_DE_LA_PLATAFORMA)
@Tag(name = "Notificaciones", description = "Avisos generados a partir de los eventos de la plataforma")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorNotificaciones {

    private final ServicioAvisos servicio;
    private final UsuarioActual usuarioActual;

    public ControladorNotificaciones(ServicioAvisos servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** Mis avisos, lo más nuevo primero (hasta 100). */
    @Operation(summary = "Lista mis avisos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @Content)
    })
    @GetMapping
    public List<AvisoRespuesta> misAvisos() {
        return servicio.deLaPersona(usuarioActual.obtener().oid());
    }

    /** Cuántos avisos no he leído; alimenta el contador del perfil. */
    @Operation(summary = "Cuenta mis avisos no leídos")
    @GetMapping("/no-leidos")
    public Map<String, Long> noLeidos() {
        return Map.of("cantidad", servicio.noLeidos(usuarioActual.obtener().oid()));
    }

    /** Marca un aviso propio como leído. */
    @Operation(summary = "Marca un aviso como leído")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aviso marcado"),
            @ApiResponse(responseCode = "403", description = "El aviso es de otra persona", content = @Content),
            @ApiResponse(responseCode = "404", description = "El aviso no existe", content = @Content)
    })
    @PatchMapping("/{id}/leido")
    public AvisoRespuesta marcarLeido(@PathVariable UUID id) {
        return servicio.marcarLeido(id, usuarioActual.obtener().oid());
    }
}
