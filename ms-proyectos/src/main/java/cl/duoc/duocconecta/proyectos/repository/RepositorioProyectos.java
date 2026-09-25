package cl.duoc.duocconecta.proyectos.repository;

import cl.duoc.duocconecta.proyectos.domain.Proyecto;
import cl.duoc.duocconecta.proyectos.domain.Visibilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepositorioProyectos extends JpaRepository<Proyecto, UUID> {

    /** Los de la vitrina: excluye los ocultos por moderación. */
    List<Proyecto> findByVisibilidadAndOcultoFalse(Visibilidad visibilidad);

    List<Proyecto> findByPropietarioId(String propietarioId);

    List<Proyecto> findByVisibilidadAndColaboradoresIdsContainingAndOcultoFalse(Visibilidad visibilidad, String usuarioId);
}
