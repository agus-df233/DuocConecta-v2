package cl.duoc.duocconecta.proyectos.dto;

import cl.duoc.duocconecta.proyectos.domain.EstadoProyecto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lo que envía el administrador para publicar o editar un proyecto abierto.
 *
 * <p>No lleva visibilidad ni colaboradores: un proyecto abierto siempre es público y no tiene
 * equipo. Sí lleva la carrera, porque se muestra como "Publicado por DuocConecta" y la vitrina
 * filtra por carrera.</p>
 */
public record ProyectoAbiertoDatos(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150)
        String nombre,

        @NotBlank(message = "El resumen es obligatorio")
        @Size(max = 200, message = "El resumen es de una línea: hasta 200 caracteres")
        String resumen,

        @Size(max = 2000)
        String descripcion,

        String urlRepositorio,

        @Size(max = 80)
        String sede,

        @NotBlank(message = "La carrera es obligatoria: es la que se muestra y la que usan los filtros")
        @Size(max = 120)
        String carrera,

        List<String> herramientas,

        @NotNull(message = "El estado del proyecto es obligatorio")
        EstadoProyecto estado,

        List<AdjuntoDatos> adjuntos
) {
}
