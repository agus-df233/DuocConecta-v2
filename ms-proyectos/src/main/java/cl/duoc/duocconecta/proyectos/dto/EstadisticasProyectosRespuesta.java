package cl.duoc.duocconecta.proyectos.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Cifras de la vitrina para el dashboard de administración.
 *
 * <p>{@code porPropietario} lleva el {@code oid} de cada dueña con su cantidad de proyectos: ms-proyectos
 * no sabe de carreras, así que el BFF lo cruza con ms-usuarios para contar por carrera.</p>
 */
@Schema(description = "Cantidad de proyectos y comentarios, en total y agrupados")
public record EstadisticasProyectosRespuesta(
        @Schema(example = "25") long total,
        @Schema(description = "Públicos y no ocultos: los que se ven en la vitrina", example = "18") long enVitrina,
        @Schema(example = "2") long ocultosPorModeracion,
        @Schema(example = "6") long publicadosUltimos30Dias,
        @Schema(example = "40") long comentarios,
        List<Conteo> porVisibilidad,
        List<Conteo> porEstado,
        List<Conteo> porSede,
        List<Conteo> porPropietario) {

    /** Una barra del gráfico: la categoría y cuántos proyectos tiene. */
    @Schema(description = "Categoría y cantidad")
    public record Conteo(@Schema(example = "Viña del Mar") String clave,
                         @Schema(example = "7") long cantidad) {
    }
}
