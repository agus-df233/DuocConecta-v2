package cl.duoc.duocconecta.usuarios.controller;

import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
import cl.duoc.duocconecta.usuarios.dto.EstadisticasUsuariosRespuesta;
import cl.duoc.duocconecta.usuarios.service.ServicioEstadisticas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lo que la administración necesita de ms-usuarios. Solo ADMINISTRADOR. */
@RestController
@RequestMapping("/api/v1/usuarios/admin")
@PreAuthorize(ReglasDeAcceso.SOLO_ADMINISTRADOR)
@Tag(name = "Administración", description = "Cifras de la comunidad para el dashboard")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorAdministracionUsuarios {

    private final ServicioEstadisticas estadisticas;

    public ControladorAdministracionUsuarios(ServicioEstadisticas estadisticas) {
        this.estadisticas = estadisticas;
    }

    /** Cantidad de perfiles en total, visibles y por rol, carrera y sede. */
    @Operation(summary = "Estadísticas de la comunidad")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cifras calculadas"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR", content = @Content)
    })
    @GetMapping("/estadisticas")
    public EstadisticasUsuariosRespuesta estadisticas() {
        return estadisticas.calcular();
    }
}
