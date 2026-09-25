package cl.duoc.duocconecta.usuarios.dto;

import cl.duoc.duocconecta.comun.seguridad.Rol;
import cl.duoc.duocconecta.usuarios.domain.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Perfil completo del usuario autenticado, con sus datos de contacto.
 *
 * <p>Solo se devuelve en los endpoints {@code /me}: es la vista que una persona tiene de sí misma.
 * Para ver a otros existe {@link PerfilPublicoRespuesta}, que no trae teléfono ni redes.</p>
 */
@Schema(description = "Perfil completo del usuario autenticado, incluidos sus datos de contacto privados")
public record PerfilPrivadoRespuesta(

        @Schema(description = "Identificador del perfil en DuocConecta")
        UUID id,

        @Schema(description = "Nombre para mostrar", example = "Juana Pérez")
        String nombre,

        @Schema(description = "Correo institucional", example = "juana.perez@duocuc.cl")
        String correo,

        @Schema(description = "Rol derivado del dominio del correo", example = "ESTUDIANTE")
        Rol rol,

        @Schema(description = "Carrera", example = "Ingeniería en Informática")
        String carrera,

        @Schema(description = "Sede", example = "Plaza Oeste")
        String sede,

        @Schema(description = "Presentación breve que se muestra en la vitrina")
        String bio,

        @Schema(description = "Indica si el perfil aparece en los listados públicos")
        boolean visible,

        @Schema(description = "Teléfono de contacto. Dato privado: no sale en respuestas públicas")
        String telefono,

        @Schema(description = "Redes sociales. Dato privado: no sale en respuestas públicas")
        List<String> redes) {

    /**
     * Arma la respuesta a partir de la entidad.
     */
    public static PerfilPrivadoRespuesta desde(Usuario usuario) {
        return new PerfilPrivadoRespuesta(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.getCarrera(),
                usuario.getSede(),
                usuario.getBio(),
                usuario.isVisible(),
                usuario.getTelefono(),
                List.copyOf(usuario.getRedes()));
    }
}
