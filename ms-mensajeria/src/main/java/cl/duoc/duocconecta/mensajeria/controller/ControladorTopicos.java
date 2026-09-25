package cl.duoc.duocconecta.mensajeria.controller;

import cl.duoc.duocconecta.comun.seguridad.ReglasDeAcceso;
import cl.duoc.duocconecta.mensajeria.dto.CrearTopicoDatos;
import cl.duoc.duocconecta.mensajeria.dto.TopicoRespuesta;
import cl.duoc.duocconecta.mensajeria.service.ServicioAdministracionColas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tópicos SNS, el equivalente de los exchanges. Solo ADMINISTRADOR. */
@RestController
@RequestMapping("/api/v1/mensajeria/topicos")
@PreAuthorize(ReglasDeAcceso.SOLO_ADMINISTRADOR)
@Tag(name = "Tópicos (exchanges)", description = "Crear, listar y eliminar tópicos SNS")
@SecurityRequirement(name = "bearer-jwt")
public class ControladorTopicos {

    private final ServicioAdministracionColas servicio;

    public ControladorTopicos(ServicioAdministracionColas servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Lista los tópicos")
    @GetMapping
    public List<TopicoRespuesta> listar() {
        return servicio.listarTopicos();
    }

    @Operation(summary = "Crea un tópico")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tópico creado"),
            @ApiResponse(responseCode = "400", description = "Nombre inválido", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TopicoRespuesta> crear(@Valid @RequestBody CrearTopicoDatos datos) {
        TopicoRespuesta creado = servicio.crearTopico(datos.nombre());
        return ResponseEntity.created(URI.create("/api/v1/mensajeria/topicos/" + creado.nombre())).body(creado);
    }

    @Operation(summary = "Elimina un tópico", description = "El tópico de la topología base está protegido.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminado"),
            @ApiResponse(responseCode = "404", description = "No existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Es parte de la topología base", content = @Content)
    })
    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(@PathVariable String nombre) {
        servicio.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }
}
