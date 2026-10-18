package cl.duoc.duocconecta.mensajeria.dto.rabbit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo para crear un exchange en RabbitMQ. */
public record CrearExchangeDatos(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre admite hasta 200 caracteres")
        @Pattern(regexp = ReglasRabbit.PATRON, message = ReglasRabbit.MENSAJE)
        @Schema(example = "duocconecta.prueba.topic")
        String nombre,

        @NotNull(message = "El tipo es obligatorio: DIRECT, TOPIC o FANOUT")
        @Schema(example = "TOPIC")
        TipoExchange tipo) {
}
