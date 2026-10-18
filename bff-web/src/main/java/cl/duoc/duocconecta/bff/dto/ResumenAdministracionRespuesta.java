package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Todo lo que muestra el dashboard de administración, en una sola respuesta. */
@Schema(description = "Cifras de la comunidad y de la vitrina para el dashboard")
public record ResumenAdministracionRespuesta(EstadisticasUsuarios comunidad, Vitrina vitrina) {

    /**
     * Cifras de la vitrina sin identificadores de personas: en vez de por dueña, por carrera.
     *
     * @param porCarrera proyectos según la carrera de quien los publicó
     */
    @Schema(description = "Proyectos en total, en vitrina, ocultos y agrupados")
    public record Vitrina(long total, long enVitrina, long ocultosPorModeracion,
                          long publicadosUltimos30Dias, long comentarios,
                          List<Conteo> porVisibilidad, List<Conteo> porEstado,
                          List<Conteo> porSede, List<Conteo> porCarrera) {
    }
}
