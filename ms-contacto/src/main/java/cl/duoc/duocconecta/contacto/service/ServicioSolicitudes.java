package cl.duoc.duocconecta.contacto.service;

import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.comun.eventos.TiposDeEvento;
import cl.duoc.duocconecta.contacto.domain.EstadoSolicitud;
import cl.duoc.duocconecta.contacto.domain.SolicitudContacto;
import cl.duoc.duocconecta.contacto.dto.DatosDeContacto;
import cl.duoc.duocconecta.contacto.dto.DecisionSolicitud;
import cl.duoc.duocconecta.contacto.dto.SolicitudDatos;
import cl.duoc.duocconecta.contacto.dto.SolicitudRespuesta;
import cl.duoc.duocconecta.contacto.exception.ConflictoDeEstadoException;
import cl.duoc.duocconecta.contacto.exception.OperacionNoPermitidaException;
import cl.duoc.duocconecta.contacto.exception.RecursoNoEncontradoException;
import cl.duoc.duocconecta.contacto.exception.SolicitudInvalidaException;
import cl.duoc.duocconecta.contacto.repository.RepositorioSolicitudes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de colaboración.
 *
 * <p>Devuelve DTOs desde dentro de la transacción: con {@code open-in-view: false} la sesión se
 * cierra al salir del servicio y traducir después sería tarde.</p>
 */
@Service
@RequiredArgsConstructor
public class ServicioSolicitudes {

    private final RepositorioSolicitudes repositorioSolicitudes;
    private final ApplicationEventPublisher eventos;

    /** Ni a uno mismo, ni dos vivas por el mismo proyecto: la bandeja no se debe poder inundar. */
    @Transactional
    public SolicitudRespuesta crear(SolicitudDatos dto, String solicitanteId) {
        if (solicitanteId.equals(dto.solicitadoId())) {
            throw new SolicitudInvalidaException("No puedes solicitar contacto contigo mismo");
        }

        repositorioSolicitudes.buscarViva(solicitanteId, dto.solicitadoId(), dto.proyectoId(),
                        List.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.ACEPTADA))
                .ifPresent(s -> {
                    throw new ConflictoDeEstadoException(s.getEstado() == EstadoSolicitud.ACEPTADA
                            ? "Ya colaboran en este proyecto."
                            : "Ya le enviaste una solicitud por este proyecto y sigue pendiente.");
                });

        // Pedir contacto es ofrecer el propio. Se guarda ahora, pero no se muestra a nadie
        // mientras la solicitud siga pendiente.
        DatosDeContacto mios = dto.contactoSolicitante() != null
                ? dto.contactoSolicitante() : DatosDeContacto.NINGUNO;

        SolicitudContacto solicitud = SolicitudContacto.builder()
                .solicitanteId(solicitanteId)
                .solicitadoId(dto.solicitadoId())
                .proyectoId(dto.proyectoId())
                .mensaje(dto.mensaje())
                .estado(EstadoSolicitud.PENDIENTE)
                .correoSolicitante(mios.correo())
                .telefonoSolicitante(mios.telefono())
                .redesSolicitante(mios.redes())
                .build();

        SolicitudContacto guardada = repositorioSolicitudes.save(solicitud);
        // Se publica tras el commit: quien la recibe se entera sin tener que revisar su bandeja.
        eventos.publishEvent(EventoDuocConecta.nuevo(TiposDeEvento.COLABORACION_SOLICITADA,
                solicitanteId, dto.solicitadoId(), guardada.getId().toString(),
                textoONulo(dto.proyectoId()), null));
        return SolicitudRespuesta.desdeEntidad(guardada);
    }

    /**
     * Solo responde la destinataria, y solo mientras siga pendiente. Los datos se guardan
     * únicamente si acepta: ese es el punto exacto del consentimiento.
     */
    @Transactional
    public SolicitudRespuesta responder(UUID id, String usuarioId, DecisionSolicitud respuesta) {
        SolicitudContacto solicitud = repositorioSolicitudes.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada: " + id));

        if (!solicitud.getSolicitadoId().equals(usuarioId)) {
            throw new OperacionNoPermitidaException(
                    "Solo el usuario solicitado puede responder esta solicitud");
        }
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new ConflictoDeEstadoException("Esta solicitud ya fue respondida");
        }

        if (respuesta.aceptar()) {
            solicitud.setEstado(EstadoSolicitud.ACEPTADA);
            solicitud.setCorreoSolicitado(respuesta.correo());
            solicitud.setTelefonoSolicitado(respuesta.telefono());
            solicitud.setRedesSolicitado(respuesta.redes());
        } else {
            solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        }
        solicitud.setFechaRespuesta(Instant.now());

        SolicitudContacto guardada = repositorioSolicitudes.save(solicitud);
        eventos.publishEvent(EventoDuocConecta.nuevo(
                respuesta.aceptar() ? TiposDeEvento.COLABORACION_ACEPTADA : TiposDeEvento.COLABORACION_RECHAZADA,
                usuarioId, guardada.getSolicitanteId(), guardada.getId().toString(),
                textoONulo(guardada.getProyectoId()), null));
        return SolicitudRespuesta.desdeEntidad(guardada);
    }

    /**
     * Solo la retira quien la pidió, y solo mientras siga pendiente: una vez respondida, la otra
     * persona ya decidió. Retirar libera el cupo para volver a pedir por el mismo proyecto.
     */
    @Transactional
    public SolicitudRespuesta retirar(UUID id, String usuarioId) {
        SolicitudContacto solicitud = repositorioSolicitudes.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada: " + id));

        if (!solicitud.getSolicitanteId().equals(usuarioId)) {
            throw new OperacionNoPermitidaException("Solo quien envió la solicitud puede retirarla");
        }
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new ConflictoDeEstadoException("Esta solicitud ya fue respondida y no se puede retirar");
        }

        solicitud.setEstado(EstadoSolicitud.RETIRADA);
        solicitud.setFechaRespuesta(Instant.now());
        SolicitudContacto guardada = repositorioSolicitudes.save(solicitud);
        eventos.publishEvent(EventoDuocConecta.nuevo(TiposDeEvento.COLABORACION_RETIRADA,
                usuarioId, guardada.getSolicitadoId(), guardada.getId().toString(),
                textoONulo(guardada.getProyectoId()), null));
        return SolicitudRespuesta.desdeEntidad(guardada);
    }

    private static String textoONulo(UUID id) {
        return id == null ? null : id.toString();
    }

    /** Solicitudes que le llegaron a esta persona, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<SolicitudRespuesta> recibidas(String usuarioId) {
        return repositorioSolicitudes.findBySolicitadoIdOrderByFechaSolicitudDesc(usuarioId).stream()
                .map(SolicitudRespuesta::desdeEntidad)
                .toList();
    }

    /** Solicitudes que esta persona envió, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<SolicitudRespuesta> enviadas(String usuarioId) {
        return repositorioSolicitudes.findBySolicitanteIdOrderByFechaSolicitudDesc(usuarioId).stream()
                .map(SolicitudRespuesta::desdeEntidad)
                .toList();
    }
}
