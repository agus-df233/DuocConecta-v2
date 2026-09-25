package cl.duoc.duocconecta.proyectos.dto;

import cl.duoc.duocconecta.proyectos.domain.EstadoProyecto;
import cl.duoc.duocconecta.proyectos.domain.Proyecto;
import cl.duoc.duocconecta.proyectos.domain.Visibilidad;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Un proyecto tal como sale de la API. */
public record ProyectoRespuesta(
        UUID id,
        String nombre,
        String resumen,
        String descripcion,
        String urlRepositorio,
        String propietarioId,
        String sede,
        List<String> herramientas,
        EstadoProyecto estado,
        Visibilidad visibilidad,
        List<String> colaboradoresIds,
        List<AdjuntoRespuesta> adjuntos,
        long cantidadComentarios,
        Instant fechaCreacion
) {
    /**
     * Arma la respuesta a partir de la entidad.
     *
     * <p>Las listas se copian y no se pasan tal cual. Es necesario: Hibernate las carga de forma
     * perezosa, y si el DTO se quedara con la referencia original, al serializar la respuesta —ya
     * fuera de la transacción— no habría sesión con la base para resolverlas y la petición
     * terminaría en error. Copiarlas aquí dentro las materializa mientras la transacción sigue
     * abierta.</p>
     *
     * @param cantidadComentarios se cuenta aparte porque los comentarios son su propia tabla
     * @param urlDeS3             cómo construir la URL pública de un archivo subido
     */
    public static ProyectoRespuesta desdeEntidad(Proyecto r, long cantidadComentarios,
                                                 Function<String, String> urlDeS3) {
        return new ProyectoRespuesta(
                r.getId(), r.getNombre(), r.getResumen(), r.getDescripcion(), r.getUrlRepositorio(),
                r.getPropietarioId(), r.getSede(), List.copyOf(r.getHerramientas()),
                r.getEstado(), r.getVisibilidad(),
                List.copyOf(r.getColaboradoresIds()),
                r.getAdjuntos().stream().map(a -> AdjuntoRespuesta.desdeEntidad(a, urlDeS3)).toList(),
                cantidadComentarios, r.getFechaCreacion());
    }
}
