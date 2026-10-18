package cl.duoc.duocconecta.usuarios.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Cifras de la comunidad para el dashboard de administración. Solo cantidades, sin datos personales. */
@Schema(description = "Cantidad de perfiles, en total y agrupados por rol, carrera y sede")
public record EstadisticasUsuariosRespuesta(
        @Schema(example = "42") long total,
        @Schema(description = "Perfiles que aparecen en búsquedas y listados", example = "38") long visibles,
        List<Conteo> porRol,
        List<Conteo> porCarrera,
        List<Conteo> porSede) {

    /** Una barra del gráfico: la categoría y cuántos perfiles tiene. */
    @Schema(description = "Categoría y cantidad")
    public record Conteo(@Schema(example = "Ingeniería en Informática") String clave,
                         @Schema(example = "12") long cantidad) {
    }
}
