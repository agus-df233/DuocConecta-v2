package cl.duoc.duocconecta.contacto.repository;

import cl.duoc.duocconecta.contacto.domain.EstadoSolicitud;
import cl.duoc.duocconecta.contacto.domain.SolicitudContacto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a las solicitudes de colaboración. */
public interface RepositorioSolicitudes extends JpaRepository<SolicitudContacto, UUID> {

    /** Bandeja de entrada: lo que le pidieron a esta persona, lo más nuevo primero. */
    List<SolicitudContacto> findBySolicitadoIdOrderByFechaSolicitudDesc(String solicitadoId);

    /** Bandeja de salida: lo que esta persona pidió, lo más nuevo primero. */
    List<SolicitudContacto> findBySolicitanteIdOrderByFechaSolicitudDesc(String solicitanteId);

    /**
     * Sirve para no permitir dos solicitudes vivas por el mismo proyecto entre las mismas personas.
     *
     * <p>Se cuenta por proyecto y no por persona: alguien puede tener varios proyectos y tiene
     * sentido pedirle contacto por cada uno.</p>
     */
    @Query("""
            SELECT s FROM SolicitudContacto s
            WHERE s.solicitanteId = :solicitanteId
              AND s.solicitadoId = :solicitadoId
              AND (s.proyectoId = :proyectoId OR (s.proyectoId IS NULL AND :proyectoId IS NULL))
              AND s.estado IN :estados
            """)
    Optional<SolicitudContacto> buscarViva(String solicitanteId, String solicitadoId,
                                           UUID proyectoId, List<EstadoSolicitud> estados);
}
