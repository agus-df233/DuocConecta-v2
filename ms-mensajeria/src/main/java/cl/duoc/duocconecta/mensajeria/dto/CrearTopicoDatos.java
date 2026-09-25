package cl.duoc.duocconecta.mensajeria.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo para crear un tópico SNS, el equivalente del exchange topic. */
public record CrearTopicoDatos(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 256, message = "SNS admite nombres de hasta 256 caracteres")
        @Pattern(regexp = ReglasDeNombres.PATRON, message = ReglasDeNombres.MENSAJE)
        @Schema(example = "duocconecta-prueba-eventos")
        String nombre) {
}
