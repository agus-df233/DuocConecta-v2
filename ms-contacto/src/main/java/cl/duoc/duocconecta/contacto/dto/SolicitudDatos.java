package cl.duoc.duocconecta.contacto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Lo que llega al pedir contacto.
 *
 * <p>Incluye los datos de quien pide: pedir contacto es ofrecer el propio. No se muestran a nadie
 * hasta que la otra persona acepta.</p>
 */
public record SolicitudDatos(

        @NotBlank(message = "El id del usuario solicitado es obligatorio")
        String solicitadoId,

        UUID proyectoId,

        @Size(max = 500)
        String mensaje,

        DatosDeContacto contactoSolicitante
) {
}
