package cl.duoc.duocconecta.usuarios.dto;

import cl.duoc.duocconecta.comun.seguridad.Rol;
import cl.duoc.duocconecta.usuarios.domain.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Perfil de otra persona, tal como lo ve la comunidad.
 *
 * <p>No incluye teléfono ni redes a propósito: son datos de contacto privados. Compartirlos
 * requiere aceptar una solicitud de colaboración, que gestiona ms-contacto.</p>
 */
@Schema(description = "Perfil público de un usuario, sin datos de contacto")
public record PerfilPublicoRespuesta(

        @Schema(description = "Identificador del perfil en DuocConecta")
        UUID id,

        @Schema(description = "Nombre para mostrar", example = "Juana Pérez")
        String nombre,

        @Schema(description = "Rol dentro de la comunidad", example = "ESTUDIANTE")
        Rol rol,

        @Schema(description = "Carrera", example = "Ingeniería en Informática")
        String carrera,

        @Schema(description = "Sede", example = "Plaza Oeste")
        String sede,

        @Schema(description = "Presentación breve")
        String bio) {

    /**
     * Arma la respuesta a partir de la entidad, dejando fuera los datos de contacto.
     */
    public static PerfilPublicoRespuesta desde(Usuario usuario) {
        return new PerfilPublicoRespuesta(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getCarrera(),
                usuario.getSede(),
                usuario.getBio());
    }
}
