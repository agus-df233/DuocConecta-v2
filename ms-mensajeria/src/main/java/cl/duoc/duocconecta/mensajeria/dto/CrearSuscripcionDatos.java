package cl.duoc.duocconecta.mensajeria.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo para suscribir una cola a un tópico, el equivalente del binding. La política de la cola
 * se agrega sola: sin ella, SNS descartaría los mensajes sin avisar.
 */
public record CrearSuscripcionDatos(
        @NotBlank(message = "El tópico es obligatorio")
        @Pattern(regexp = ReglasDeNombres.PATRON, message = ReglasDeNombres.MENSAJE)
        @Schema(example = "duocconecta-eventos")
        String topico,

        @NotBlank(message = "La cola es obligatoria")
        @Pattern(regexp = ReglasDeNombres.PATRON, message = ReglasDeNombres.MENSAJE)
        @Schema(example = "duocconecta-prueba")
        String cola,

        @Pattern(regexp = ReglasDeNombres.PATRON_FILTRO,
                message = "El filtro es un prefijo de tipo en minúsculas terminado en punto, por ejemplo proyecto.")
        @Schema(description = "Prefijo de tipo; vacío = recibe todo (el # de RabbitMQ)", example = "proyecto.")
        String filtroPrefijo,

        @Pattern(regexp = ReglasDeNombres.PATRON, message = ReglasDeNombres.MENSAJE)
        @Schema(description = "DLQ de la suscripción; por defecto duocconecta-eventos-no-entregados")
        String colaNoEntregados) {
}
