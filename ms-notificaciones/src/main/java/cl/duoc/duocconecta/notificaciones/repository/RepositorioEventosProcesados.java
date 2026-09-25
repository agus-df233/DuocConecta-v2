package cl.duoc.duocconecta.notificaciones.repository;

import cl.duoc.duocconecta.notificaciones.domain.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioEventosProcesados extends JpaRepository<EventoProcesado, EventoProcesado.Clave> {
}
