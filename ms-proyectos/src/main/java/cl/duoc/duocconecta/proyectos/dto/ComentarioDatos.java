package cl.duoc.duocconecta.proyectos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lo que se envía para comentar un proyecto. */
public record ComentarioDatos(

        @NotBlank(message = "El comentario no puede ir vacío")
        @Size(max = 1000, message = "El comentario admite hasta 1000 caracteres")
        String texto
) {
}
