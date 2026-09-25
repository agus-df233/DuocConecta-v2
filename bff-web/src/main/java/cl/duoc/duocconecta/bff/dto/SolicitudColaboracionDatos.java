package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Lo que el frontend envía para pedirle contacto a otra persona.
 *
 * <p>Pedir contacto es ofrecer el propio: el BFF adjunta los datos de quien pide, y por eso aquí
 * solo viaja la decisión sobre el teléfono, que es el dato más sensible.</p>
 */
@Schema(description = "Solicitud de colaboración hacia otra persona")
public record SolicitudColaboracionDatos(

        @NotBlank(message = "Falta indicar a quién le pides contacto")
        @Schema(description = "Identificador de la persona a la que se le pide el contacto")
        String solicitadoId,

        @Schema(description = "Proyecto de la vitrina desde el que sale la solicitud")
        UUID proyectoId,

        @Size(max = 500)
        @Schema(description = "Mensaje para quien recibe la solicitud")
        String mensaje,

        @Schema(description = "Si se comparte también el teléfono propio al pedir contacto")
        boolean compartirTelefono) {
}
