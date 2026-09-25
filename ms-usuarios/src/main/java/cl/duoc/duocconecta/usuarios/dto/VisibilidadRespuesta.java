package cl.duoc.duocconecta.usuarios.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estado de visibilidad del perfil después de alternarlo.
 */
@Schema(description = "Estado de visibilidad del perfil tras el cambio")
public record VisibilidadRespuesta(

        @Schema(description = "true si el perfil aparece en los listados públicos", example = "true")
        boolean visible,

        @Schema(description = "Explicación en palabras del estado actual",
                example = "Tu perfil ahora aparece en las búsquedas.")
        String mensaje) {

    /**
     * Arma la respuesta agregando el mensaje que corresponde al nuevo estado.
     */
    public static VisibilidadRespuesta de(boolean visible) {
        String mensaje = visible
                ? "Tu perfil ahora aparece en las búsquedas."
                : "Tu perfil quedó oculto de las búsquedas. No se borró nada.";
        return new VisibilidadRespuesta(visible, mensaje);
    }
}
