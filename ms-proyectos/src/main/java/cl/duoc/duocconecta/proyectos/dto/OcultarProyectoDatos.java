package cl.duoc.duocconecta.proyectos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo para ocultar un proyecto. El motivo le llega a la dueña en su aviso. */
public record OcultarProyectoDatos(
        @NotBlank(message = "Indica el motivo: la dueña lo recibe en su aviso")
        @Size(max = 500, message = "El motivo admite hasta 500 caracteres")
        String motivo) {
}
