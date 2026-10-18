package cl.duoc.duocconecta.mensajeria.dto.rabbit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo para crear o borrar un binding: une una cola a un exchange con una routing key. */
public record BindingDatos(
        @NotBlank(message = "El exchange es obligatorio")
        @Pattern(regexp = ReglasRabbit.PATRON, message = ReglasRabbit.MENSAJE)
        @Schema(example = "duocconecta.prueba.topic")
        String exchange,

        @NotBlank(message = "La cola es obligatoria")
        @Pattern(regexp = ReglasRabbit.PATRON, message = ReglasRabbit.MENSAJE)
        @Schema(example = "duocconecta.prueba")
        String cola,

        @NotBlank(message = "La routing key es obligatoria (usa # para recibir todo)")
        @Size(max = 255, message = "La routing key admite hasta 255 caracteres")
        @Pattern(regexp = ReglasRabbit.PATRON_ROUTING, message = ReglasRabbit.MENSAJE_ROUTING)
        @Schema(example = "proyecto.#")
        String routingKey) {
}
