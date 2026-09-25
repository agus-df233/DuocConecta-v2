package cl.duoc.duocconecta.notificaciones.repository;

import cl.duoc.duocconecta.notificaciones.domain.EventoAuditado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioEventosAuditados extends JpaRepository<EventoAuditado, String> {
}
