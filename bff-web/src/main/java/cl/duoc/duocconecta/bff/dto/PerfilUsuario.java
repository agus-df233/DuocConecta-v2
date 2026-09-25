package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Perfil tal como lo devuelve ms-usuarios.
 *
 * <p>El BFF lo redefine en vez de importar el DTO del microservicio a propósito: si mañana
 * ms-usuarios agrega o renombra un campo, el BFF no se rompe ni arrastra el cambio al frontend
 * sin que nadie lo decida.</p>
 */
@Schema(description = "Perfil del usuario autenticado")
public record PerfilUsuario(
        String id,
        String nombre,
        String correo,
        String rol,
        String carrera,
        String sede,
        String bio,
        boolean visible,
        String telefono,
        List<String> redes) {
}
