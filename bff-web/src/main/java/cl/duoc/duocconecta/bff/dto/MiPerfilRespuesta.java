package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Todo lo que la pantalla de perfil del frontend necesita, en una sola respuesta.
 *
 * <p>Es el punto del BFF: en vez de que el navegador haga dos llamadas (perfil y redes), el BFF
 * las hace por él y devuelve el resultado ya armado.</p>
 */
@Schema(description = "Perfil y redes del usuario autenticado, agregados en una sola respuesta")
public record MiPerfilRespuesta(

        @Schema(description = "Datos del perfil")
        PerfilUsuario perfil,

        @Schema(description = "Redes sociales del usuario autenticado")
        List<String> redes,

        @Schema(description = "Indica si el perfil todavía no tiene carrera, sede ni biografía. "
                + "El frontend lo usa para invitar a completar los datos.")
        boolean perfilIncompleto) {

    /**
     * Arma la respuesta agregada y calcula si el perfil está a medio llenar.
     *
     * <p>Se considera incompleto cuando falta cualquiera de los tres datos que la vitrina muestra:
     * carrera, sede o biografía.</p>
     */
    public static MiPerfilRespuesta de(PerfilUsuario perfil, List<String> redes) {
        boolean incompleto = estaVacio(perfil.carrera())
                || estaVacio(perfil.sede())
                || estaVacio(perfil.bio());

        return new MiPerfilRespuesta(perfil, redes == null ? List.of() : redes, incompleto);
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
