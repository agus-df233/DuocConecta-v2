package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un proyecto de la vitrina, con los datos de quien lo publicó ya resueltos.
 *
 * <p>ms-proyectos guarda solo el identificador del propietario; el nombre, la carrera y la sede
 * los agrega el BFF consultando a ms-usuarios. Sin eso la vitrina mostraría identificadores
 * opacos y no podría filtrar por carrera.</p>
 */
@Schema(description = "Proyecto de la vitrina")
public record ProyectoRespuesta(
        UUID id,
        String nombre,
        @Schema(description = "Una línea; es lo que se lee en la tarjeta") String resumen,
        @Schema(description = "El detalle completo, que se lee al abrir el proyecto") String descripcion,
        String urlRepositorio,
        String propietarioId,
        @Schema(description = "Quien lo publicó; los campos van en null si ocultó su perfil")
        Autor autor,
        String sede,
        @Schema(description = "Herramientas y tecnologías; texto libre") List<String> herramientas,
        String estado,
        String visibilidad,
        List<String> colaboradoresIds,
        List<AdjuntoRespuesta> adjuntos,
        long cantidadComentarios,
        Instant fechaCreacion) {

    /** Copia el proyecto sumándole los datos de quien lo publicó. */
    public ProyectoRespuesta conAutor(Autor quienPublico) {
        return new ProyectoRespuesta(id, nombre, resumen, descripcion, urlRepositorio, propietarioId,
                quienPublico, sede, herramientas, estado, visibilidad, colaboradoresIds,
                adjuntos, cantidadComentarios, fechaCreacion);
    }
}
