package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El texto que se envía al comentar un proyecto. */
@Schema(description = "Comentario a publicar")
public record ComentarioDatos(

        @NotBlank(message = "El comentario no puede ir vacío")
        @Size(max = 1000, message = "El comentario admite hasta 1000 caracteres")
        String texto) {
}
