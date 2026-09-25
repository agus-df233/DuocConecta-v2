package cl.duoc.duocconecta.proyectos.controller;

import cl.duoc.duocconecta.proyectos.dto.FirmaAdjuntoDatos;
import cl.duoc.duocconecta.proyectos.dto.FirmaAdjuntoRespuesta;
import cl.duoc.duocconecta.proyectos.service.ServicioAdjuntos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autorización para subir archivos adjuntos.
 *
 * <p>No recibe el identificador del proyecto: al crear uno, los archivos se eligen antes de que el
 * proyecto exista. Las claves devueltas viajan después dentro del cuerpo que crea o edita.</p>
 */
@RestController
@RequestMapping("/api/v1/proyectos/adjuntos")
@Tag(name = "Adjuntos", description = "Subida de archivos a los proyectos")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorAdjuntos {

    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    private final ServicioAdjuntos servicioAdjuntos;

    public ControladorAdjuntos(ServicioAdjuntos servicioAdjuntos) {
        this.servicioAdjuntos = servicioAdjuntos;
    }

    /**
     * Autoriza la subida de un archivo y devuelve a dónde mandarlo.
     *
     * <p>El navegador sube directo a S3 con la URL firmada. El archivo no pasa por el servidor.</p>
     */
    @Operation(summary = "Autoriza subir un archivo",
            description = "Devuelve una URL temporal para subir directo a S3. El tipo de contenido "
                    + "del PUT tiene que ser el mismo que se declara aquí.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subida autorizada"),
            @ApiResponse(responseCode = "400", description = "Tipo de archivo o tamaño no admitido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @Content)
    })
    @PostMapping("/firma")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<FirmaAdjuntoRespuesta> firmar(@Valid @RequestBody FirmaAdjuntoDatos datos) {
        return ResponseEntity.ok(servicioAdjuntos.firmarSubida(datos));
    }
}
