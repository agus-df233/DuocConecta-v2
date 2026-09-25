package cl.duoc.duocconecta.bff.controller;

import cl.duoc.duocconecta.bff.dto.MiPerfilRespuesta;
import cl.duoc.duocconecta.bff.service.ClienteUsuarios;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de agregación que consume el frontend React.
 */
@RestController
@RequestMapping("/api/v1/bff")
@Tag(name = "BFF", description = "Respuestas agregadas para las pantallas del frontend")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorPerfilBff {

    /** Roles que pueden operar sobre su propio perfil. */
    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    private final ClienteUsuarios clienteUsuarios;

    public ControladorPerfilBff(ClienteUsuarios clienteUsuarios) {
        this.clienteUsuarios = clienteUsuarios;
    }

    /**
     * Devuelve todo lo que la pantalla de perfil necesita en una sola llamada.
     *
     * <p>Por dentro consulta a ms-usuarios dos veces (el perfil y las redes) propagando el token
     * del usuario, y junta ambas respuestas. Así el navegador hace una petición en vez de dos.</p>
     *
     * @return 200 con el perfil, las redes y un indicador de si falta completar datos
     */
    @Operation(
            summary = "Devuelve el perfil y las redes del usuario autenticado",
            description = "Junta el perfil y las redes en una sola respuesta. Si es el primer ingreso, el perfil se crea en ese momento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil y redes obtenidos correctamente"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @Content)
    })
    @GetMapping("/mi-perfil")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<MiPerfilRespuesta> obtenerMiPerfil() {
        return ResponseEntity.ok(MiPerfilRespuesta.de(
                clienteUsuarios.obtenerPerfilPropio(),
                clienteUsuarios.obtenerRedesPropias()));
    }
}
