package cl.duoc.duocconecta.mensajeria.dto.rabbit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo para crear una cola en RabbitMQ, opcionalmente con su DLQ. */
@Schema(description = "Nueva cola quorum de RabbitMQ, opcionalmente con su DLQ")
public record CrearColaRabbitDatos(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre admite hasta 200 caracteres")
        @Pattern(regexp = ReglasRabbit.PATRON, message = ReglasRabbit.MENSAJE)
        @Schema(example = "duocconecta.prueba")
        String nombre,

        @Schema(description = "Crear también la DLQ <nombre>.descartes, enlazada al DLX; por defecto sí")
        Boolean conDescarte,

        @Min(value = 1, message = "Se necesita al menos 1 entrega antes de la DLQ")
        @Max(value = 20, message = "Hasta 20 entregas antes de la DLQ")
        @Schema(description = "Entregas antes de mandarla al DLX (x-delivery-limit); por defecto 3", example = "3")
        Integer maxEntregas) {

    public boolean quiereDescarte() {
        return conDescarte == null || conDescarte;
    }

    /** Sin DLQ, el límite de entregas no tiene a dónde mandar el mensaje. */
    @AssertTrue(message = "maxEntregas solo aplica si la cola tiene DLQ")
    @Schema(hidden = true)
    public boolean isEntregasConDescarte() {
        return quiereDescarte() || maxEntregas == null;
    }
}
