package cl.duoc.duocconecta.proyectos.repository;

import cl.duoc.duocconecta.proyectos.domain.Comentario;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los comentarios de los proyectos. */
public interface RepositorioComentarios extends JpaRepository<Comentario, UUID> {

    /** Hilo de un proyecto, del más antiguo al más nuevo: se lee como una conversación. */
    List<Comentario> findByProyectoIdOrderByFechaAsc(UUID proyectoId);

    /** Cuántos comentarios tiene un proyecto; la tarjeta de la vitrina lo muestra. */
    long countByProyectoId(UUID proyectoId);
}
