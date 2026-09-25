package cl.duoc.duocconecta.proyectos.service;

import cl.duoc.duocconecta.proyectos.domain.Comentario;
import cl.duoc.duocconecta.proyectos.domain.Proyecto;
import cl.duoc.duocconecta.proyectos.dto.ComentarioDatos;
import cl.duoc.duocconecta.proyectos.dto.ComentarioRespuesta;
import cl.duoc.duocconecta.proyectos.exception.OperacionNoPermitidaException;
import cl.duoc.duocconecta.proyectos.exception.RecursoNoEncontradoException;
import cl.duoc.duocconecta.proyectos.repository.RepositorioComentarios;
import cl.duoc.duocconecta.proyectos.repository.RepositorioProyectos;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comentarios de la vitrina.
 *
 * <p>Una sola regla, para leer y escribir: comenta quien puede ver el proyecto. Así un proyecto
 * privado no filtra su conversación.</p>
 */
@Service
@RequiredArgsConstructor
public class ServicioComentarios {

    private final RepositorioComentarios repositorioComentarios;
    private final RepositorioProyectos repositorioProyectos;

    /** El hilo completo, del más antiguo al más nuevo. */
    @Transactional(readOnly = true)
    public List<ComentarioRespuesta> listar(UUID proyectoId, String usuarioId) {
        exigirAcceso(proyectoId, usuarioId);
        return repositorioComentarios.findByProyectoIdOrderByFechaAsc(proyectoId).stream()
                .map(ComentarioRespuesta::desdeEntidad)
                .toList();
    }

    /** Comentar no comparte ningún dato de contacto. */
    @Transactional
    public ComentarioRespuesta crear(UUID proyectoId, ComentarioDatos dto, String usuarioId) {
        exigirAcceso(proyectoId, usuarioId);

        Comentario comentario = Comentario.builder()
                .proyectoId(proyectoId)
                .autorId(usuarioId)
                .texto(dto.texto().trim())
                .build();

        return ComentarioRespuesta.desdeEntidad(repositorioComentarios.save(comentario));
    }

    /** El proyecto existe y la persona puede verlo. */
    private void exigirAcceso(UUID proyectoId, String usuarioId) {
        Proyecto proyecto = repositorioProyectos.findById(proyectoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + proyectoId));

        if (!proyecto.esVisiblePara(usuarioId)) {
            throw new OperacionNoPermitidaException("No tienes acceso a este proyecto");
        }
    }
}
