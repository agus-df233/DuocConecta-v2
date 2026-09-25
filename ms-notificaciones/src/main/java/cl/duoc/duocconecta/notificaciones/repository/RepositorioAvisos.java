package cl.duoc.duocconecta.notificaciones.repository;

import cl.duoc.duocconecta.notificaciones.domain.Aviso;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioAvisos extends JpaRepository<Aviso, UUID> {

    /** La bandeja de una persona, lo más nuevo primero. */
    List<Aviso> findTop100ByDestinatarioIdOrderByCreadoEnDesc(String destinatarioId);

    long countByDestinatarioIdAndLeidoFalse(String destinatarioId);

    long countByIdEvento(String idEvento);
}
