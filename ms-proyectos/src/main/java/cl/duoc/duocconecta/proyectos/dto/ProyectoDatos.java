package cl.duoc.duocconecta.proyectos.dto;

import cl.duoc.duocconecta.proyectos.domain.EstadoProyecto;
import cl.duoc.duocconecta.proyectos.domain.Visibilidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Lo que se envía para publicar o editar un proyecto. */
public record ProyectoDatos(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150)
        String nombre,

        /** Una línea para la tarjeta de la vitrina. */
        @NotBlank(message = "El resumen es obligatorio")
        @Size(max = 200, message = "El resumen es de una línea: hasta 200 caracteres")
        String resumen,

        /** El detalle completo, que se lee al abrir el proyecto. */
        @Size(max = 2000)
        String descripcion,

        String urlRepositorio,

        String sede,

        /** Herramientas y tecnologías; texto libre para que sirva a cualquier carrera. */
        List<String> herramientas,

        @NotNull(message = "El estado del proyecto es obligatorio")
        EstadoProyecto estado,

        @NotNull(message = "La visibilidad es obligatoria")
        Visibilidad visibilidad,

        List<String> colaboradoresIds,

        List<AdjuntoDatos> adjuntos
) {
}
