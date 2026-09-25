package cl.duoc.duocconecta.contacto.dto;

import cl.duoc.duocconecta.contacto.domain.EstadoSolicitud;
import cl.duoc.duocconecta.contacto.domain.SolicitudContacto;
import java.time.Instant;
import java.util.UUID;

/**
 * Cómo se ve una solicitud de colaboración hacia afuera.
 *
 * <p>Los datos de las dos partes solo tienen contenido cuando el estado es ACEPTADA: antes de la
 * aceptación no hay nada que mostrar, y esa es justamente la garantía de consentimiento. Quién ve
 * cuál de los dos lados lo decide el BFF, que es el que sabe quién está mirando.</p>
 */
public record SolicitudRespuesta(
        UUID id,
        String solicitanteId,
        String solicitadoId,
        UUID proyectoId,
        String mensaje,
        EstadoSolicitud estado,
        DatosDeContacto contactoSolicitante,
        DatosDeContacto contactoSolicitado,
        Instant fechaSolicitud,
        Instant fechaRespuesta
) {
    /** Traduce la entidad a la respuesta de la API. Nunca se expone la entidad JPA. */
    public static SolicitudRespuesta desdeEntidad(SolicitudContacto s) {
        boolean aceptada = s.getEstado() == EstadoSolicitud.ACEPTADA;
        return new SolicitudRespuesta(
                s.getId(), s.getSolicitanteId(), s.getSolicitadoId(), s.getProyectoId(),
                s.getMensaje(), s.getEstado(),
                // Mientras no haya aceptación no sale ningún dato, ni siquiera el de quien pidió.
                aceptada ? new DatosDeContacto(s.getCorreoSolicitante(), s.getTelefonoSolicitante(),
                        s.getRedesSolicitante()) : DatosDeContacto.NINGUNO,
                aceptada ? new DatosDeContacto(s.getCorreoSolicitado(), s.getTelefonoSolicitado(),
                        s.getRedesSolicitado()) : DatosDeContacto.NINGUNO,
                s.getFechaSolicitud(), s.getFechaRespuesta());
    }
}
