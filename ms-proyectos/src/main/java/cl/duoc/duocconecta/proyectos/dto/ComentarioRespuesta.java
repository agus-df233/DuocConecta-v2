package cl.duoc.duocconecta.proyectos.dto;

import cl.duoc.duocconecta.proyectos.domain.Comentario;
import java.time.Instant;
import java.util.UUID;

/** Un comentario tal como sale de la API. El nombre del autor lo resuelve el BFF. */
public record ComentarioRespuesta(
        UUID id,
        UUID proyectoId,
        String autorId,
        String texto,
        Instant fecha
) {
    public static ComentarioRespuesta desdeEntidad(Comentario c) {
        return new ComentarioRespuesta(
                c.getId(), c.getProyectoId(), c.getAutorId(), c.getTexto(), c.getFecha());
    }
}
