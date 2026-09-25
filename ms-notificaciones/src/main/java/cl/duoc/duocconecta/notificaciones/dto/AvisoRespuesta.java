package cl.duoc.duocconecta.notificaciones.dto;

import cl.duoc.duocconecta.notificaciones.domain.Aviso;
import java.time.Instant;
import java.util.UUID;

/** Un aviso tal como sale de la API. */
public record AvisoRespuesta(UUID id, String tipo, String titulo, String detalle, String recursoId,
                             String proyectoId, boolean leido, Instant creadoEn) {

    public static AvisoRespuesta desdeEntidad(Aviso a) {
        return new AvisoRespuesta(a.getId(), a.getTipo(), a.getTitulo(), a.getDetalle(),
                a.getRecursoId(), a.getProyectoId(), a.isLeido(), a.getCreadoEn());
    }
}
