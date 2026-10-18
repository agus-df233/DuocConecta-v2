package cl.duoc.duocconecta.bff.controller;

import cl.duoc.duocconecta.bff.dto.ResumenAdministracionRespuesta;
import cl.duoc.duocconecta.bff.service.ServicioAdministracion;
import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
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

/** El dashboard de la vista de administración. Solo ADMINISTRADOR. */
@RestController
@RequestMapping("/api/v1/bff/admin")
@PreAuthorize(ReglasDeAcceso.SOLO_ADMINISTRADOR)
@Tag(name = "BFF · Administración", description = "Cifras agregadas para el dashboard de administración")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorAdministracionBff {

    private final ServicioAdministracion servicio;

    public ControladorAdministracionBff(ServicioAdministracion servicio) {
        this.servicio = servicio;
    }

    /**
     * Cifras de la comunidad y de la vitrina en una sola llamada, con los proyectos contados por
     * la carrera de quien los publicó.
     */
    @Operation(summary = "Resumen para el dashboard de administración")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen armado"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMINISTRADOR", content = @Content)
    })
    @GetMapping("/resumen")
    public ResumenAdministracionRespuesta resumen() {
        return servicio.resumen();
    }
}
