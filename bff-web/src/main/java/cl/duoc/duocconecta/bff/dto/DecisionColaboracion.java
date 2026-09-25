package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta a una solicitud recibida.
 *
 * <p>Los datos de contacto no se escriben a mano: los arma el BFF. Aquí solo se decide si se acepta
 * y si se suma el teléfono, que es el dato más sensible.</p>
 */
@Schema(description = "Aceptación o rechazo de una solicitud recibida")
public record DecisionColaboracion(

        @Schema(description = "true acepta y comparte los datos de contacto; false rechaza")
        boolean aceptar,

        @Schema(description = "Si se acepta, incluir también el teléfono además del correo y las redes")
        boolean compartirTelefono) {
}
